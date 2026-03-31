(ns et.blog.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [clojure.string :as str]
            [et.blog.migrations :as migrations]
            [honey.sql :as sql]))

(def jdbc-opts {:builder-fn rs/as-unqualified-maps})

(defn init-conn [{:keys [type path]}]
  (let [db-spec (case type
                  :sqlite-memory {:dbtype "sqlite" :dbname "file::memory:?cache=shared"}
                  :sqlite-file {:dbtype "sqlite" :dbname path})
        ds (jdbc/get-datasource db-spec)
        persistent-conn (when (= type :sqlite-memory) (jdbc/get-connection ds))
        conn-for-use (or persistent-conn ds)]
    (migrations/migrate! conn-for-use)
    {:conn conn-for-use
     :persistent-conn persistent-conn
     :type type}))

(defn get-conn [ds]
  (if (map? ds) (:conn ds) ds))

(declare next-post-id)

(defn next-article-id [ds]
  (let [conn (get-conn ds)
        result (jdbc/execute-one! conn
                 (sql/format {:select [[[:coalesce [:max :article_id] 0]]]
                              :from [:articles]})
                 jdbc-opts)]
    (inc (first (vals result)))))

(defn create-article! [ds {:keys [title subtitle content footnotes addenda preamble preview-image abstract publish? post-content]}]
  (let [conn (get-conn ds)
        article-id (next-article-id ds)
        version (if publish? 1 0)]
    (jdbc/execute-one! conn
      (sql/format {:insert-into :articles
                   :values [{:article_id article-id
                             :title title
                             :subtitle (or subtitle "")
                             :content content
                             :footnotes (or footnotes "")
                             :addenda (or addenda "")
                             :preamble (or preamble "")
                             :version version}]})
      jdbc-opts)
    (jdbc/execute-one! conn
      (sql/format {:insert-into :article_meta
                   :values [{:article_id article-id
                             :preview_image (or preview-image "")
                             :abstract (or abstract "")}]})
      jdbc-opts)
    (when (and publish? post-content)
      (let [post-id (next-post-id ds)
            post-image (or preview-image "")]
        (jdbc/execute-one! conn
          (sql/format {:insert-into :posts
                       :values [{:post_id post-id
                                 :content post-content
                                 :footnotes ""
                                 :image post-image}]})
          jdbc-opts)
        (jdbc/execute-one! conn
          (sql/format {:insert-into :post_meta
                       :values [{:post_id post-id}]})
          jdbc-opts)
        (jdbc/execute-one! conn
          (sql/format {:insert-into :article_posts
                       :values [{:article_id article-id
                                 :article_version version
                                 :post_id post-id}]})
          jdbc-opts)))
    article-id))

(defn- current-version [ds article-id]
  (let [conn (get-conn ds)
        result (jdbc/execute-one! conn
                 (sql/format {:select [:version]
                              :from [:articles]
                              :where [:= :article_id article-id]
                              :order-by [[:created_at :desc]]
                              :limit 1})
                 jdbc-opts)]
    (or (:version result) 0)))

(defn update-article! [ds article-id {:keys [title subtitle content footnotes addenda preamble preview-image abstract publish? post-content]}]
  (let [conn (get-conn ds)
        cur (current-version ds article-id)
        version (if publish? (inc cur) cur)]
    (jdbc/execute-one! conn
      (sql/format {:insert-into :articles
                   :values [{:article_id article-id
                             :title title
                             :subtitle (or subtitle "")
                             :content content
                             :footnotes (or footnotes "")
                             :addenda (or addenda "")
                             :preamble (or preamble "")
                             :version version}]})
      jdbc-opts)
    (let [meta-updates (cond-> {}
                        preview-image (assoc :preview_image preview-image)
                        abstract (assoc :abstract abstract))]
      (when (seq meta-updates)
        (jdbc/execute-one! conn
          (sql/format {:update :article_meta
                       :set meta-updates
                       :where [:= :article_id article-id]}))))
    (when (and publish? post-content)
      (let [post-id (next-post-id ds)
            current-meta (jdbc/execute-one! conn
                           (sql/format {:select [:preview_image]
                                        :from [:article_meta]
                                        :where [:= :article_id article-id]})
                           jdbc-opts)
            post-image (or preview-image (:preview_image current-meta) "")]
        (jdbc/execute-one! conn
          (sql/format {:insert-into :posts
                       :values [{:post_id post-id
                                 :content post-content
                                 :footnotes ""
                                 :image post-image}]})
          jdbc-opts)
        (jdbc/execute-one! conn
          (sql/format {:insert-into :post_meta
                       :values [{:post_id post-id}]})
          jdbc-opts)
        (jdbc/execute-one! conn
          (sql/format {:insert-into :article_posts
                       :values [{:article_id article-id
                                 :article_version version
                                 :post_id post-id}]})
          jdbc-opts)))))

(def ^:private article-cols [:article_id :title :subtitle :content :footnotes :addenda :preamble :created_at :version])

(defn- article-select-cols []
  (into (mapv #(keyword (str "a." (name %))) article-cols)
        [:am.preview_image :am.abstract]))

(defn list-articles [ds {:keys [published-only?]}]
  (let [conn (get-conn ds)]
    (if published-only?
      (jdbc/execute! conn
        ["SELECT a.article_id, a.title, a.subtitle, a.content, a.footnotes, a.addenda, a.preamble, a.created_at, a.version, am.preview_image, am.abstract
          FROM articles a
          INNER JOIN (
            SELECT article_id, MAX(created_at) AS max_created_at
            FROM articles
            WHERE version > 0
            GROUP BY article_id
          ) latest ON a.article_id = latest.article_id AND a.created_at = latest.max_created_at
          INNER JOIN article_meta am ON am.article_id = a.article_id AND am.deleted = 0
          ORDER BY a.created_at DESC"]
        jdbc-opts)
      (jdbc/execute! conn
        ["SELECT a.article_id, a.title, a.subtitle, a.content, a.footnotes, a.addenda, a.preamble, a.created_at, a.version, am.preview_image, am.abstract
          FROM articles a
          INNER JOIN (
            SELECT article_id, MAX(created_at) AS max_created_at
            FROM articles
            GROUP BY article_id
          ) latest ON a.article_id = latest.article_id AND a.created_at = latest.max_created_at
          INNER JOIN article_meta am ON am.article_id = a.article_id AND am.deleted = 0
          ORDER BY a.created_at DESC"]
        jdbc-opts))))

(defn list-draft-articles [ds]
  (let [conn (get-conn ds)]
    (jdbc/execute! conn
      ["SELECT a.article_id, a.title, a.subtitle, a.content, a.footnotes, a.addenda, a.preamble, a.created_at, a.version, am.preview_image, am.abstract
        FROM articles a
        INNER JOIN (
          SELECT article_id, MAX(created_at) AS max_created_at
          FROM articles
          GROUP BY article_id
          HAVING MAX(version) = 0
        ) latest ON a.article_id = latest.article_id AND a.created_at = latest.max_created_at
        INNER JOIN article_meta am ON am.article_id = a.article_id AND am.deleted = 0
        ORDER BY a.created_at DESC"]
      jdbc-opts)))

(defn list-deleted-articles [ds]
  (let [conn (get-conn ds)]
    (jdbc/execute! conn
      ["SELECT a.article_id, a.title, a.subtitle, a.content, a.footnotes, a.addenda, a.preamble, a.created_at, a.version, am.preview_image, am.abstract
        FROM articles a
        INNER JOIN (
          SELECT article_id, MAX(created_at) AS max_created_at
          FROM articles
          GROUP BY article_id
        ) latest ON a.article_id = latest.article_id AND a.created_at = latest.max_created_at
        INNER JOIN article_meta am ON am.article_id = a.article_id AND am.deleted = 1
        ORDER BY a.created_at DESC"]
      jdbc-opts)))

(defn get-article [ds article-id {:keys [published-only? include-deleted?]}]
  (let [conn (get-conn ds)
        base [:and [:= :a.article_id article-id]]
        base (if published-only? (conj base [:> :a.version 0]) base)
        base (if include-deleted? base (conj base [:= :am.deleted 0]))]
    (jdbc/execute-one! conn
      (sql/format {:select (article-select-cols)
                   :from [[:articles :a]]
                   :join [[:article_meta :am] [:= :am.article_id :a.article_id]]
                   :where base
                   :order-by [[:a.created_at :desc]]
                   :limit 1})
      jdbc-opts)))

(defn get-article-version [ds article-id as-of {:keys [include-deleted?]}]
  (let [conn (get-conn ds)
        base [:and [:= :a.article_id article-id] [:<= :a.created_at as-of]]
        base (if include-deleted? base (conj base [:= :am.deleted 0]))]
    (jdbc/execute-one! conn
      (sql/format {:select (article-select-cols)
                   :from [[:articles :a]]
                   :join [[:article_meta :am] [:= :am.article_id :a.article_id]]
                   :where base
                   :order-by [[:a.created_at :desc]]
                   :limit 1})
      jdbc-opts)))

(defn get-article-by-version [ds article-id version {:keys [include-deleted?]}]
  (let [conn (get-conn ds)
        base [:and [:= :a.article_id article-id] [:= :a.version version]]
        base (if include-deleted? base (conj base [:= :am.deleted 0]))]
    (jdbc/execute-one! conn
      (sql/format {:select (article-select-cols)
                   :from [[:articles :a]]
                   :join [[:article_meta :am] [:= :am.article_id :a.article_id]]
                   :where base
                   :order-by [[:a.created_at :desc]]
                   :limit 1})
      jdbc-opts)))

(defn get-article-versions [ds article-id {:keys [published-only? include-deleted?]}]
  (let [conn (get-conn ds)
        base [:and [:= :a.article_id article-id]]
        base (if published-only? (conj base [:> :a.version 0]) base)
        base (if include-deleted? base (conj base [:= :am.deleted 0]))]
    (jdbc/execute! conn
      (sql/format {:select (article-select-cols)
                   :from [[:articles :a]]
                   :join [[:article_meta :am] [:= :am.article_id :a.article_id]]
                   :where base
                   :order-by [[:a.created_at :desc]]})
      jdbc-opts)))

;; --- Posts ---

(def ^:private post-cols [:post_id :content :footnotes :image :created_at])

(defn next-post-id [ds]
  (let [conn (get-conn ds)
        result (jdbc/execute-one! conn
                 (sql/format {:select [[[:coalesce [:max :post_id] 0]]]
                              :from [:posts]})
                 jdbc-opts)]
    (inc (first (vals result)))))

(defn create-post! [ds {:keys [content footnotes image]}]
  (let [conn (get-conn ds)
        post-id (next-post-id ds)]
    (jdbc/execute-one! conn
      (sql/format {:insert-into :posts
                   :values [{:post_id post-id
                             :content (or content "")
                             :footnotes (or footnotes "")
                             :image (or image "")}]})
      jdbc-opts)
    (jdbc/execute-one! conn
      (sql/format {:insert-into :post_meta
                   :values [{:post_id post-id}]})
      jdbc-opts)
    post-id))

(defn update-post! [ds post-id {:keys [content footnotes image]}]
  (let [conn (get-conn ds)]
    (jdbc/execute-one! conn
      (sql/format {:insert-into :posts
                   :values [{:post_id post-id
                             :content (or content "")
                             :footnotes (or footnotes "")
                             :image (or image "")}]})
      jdbc-opts)))

(defn list-posts [ds]
  (let [conn (get-conn ds)]
    (jdbc/execute! conn
      ["SELECT p.post_id, p.content, p.footnotes, p.image, p.created_at, latest.first_at
        FROM posts p
        INNER JOIN (
          SELECT post_id, MAX(created_at) AS max_created_at, MIN(created_at) AS first_at
          FROM posts
          GROUP BY post_id
        ) latest ON p.post_id = latest.post_id AND p.created_at = latest.max_created_at
        INNER JOIN post_meta pm ON pm.post_id = p.post_id AND pm.deleted = 0
        ORDER BY latest.first_at DESC"]
      jdbc-opts)))

(defn list-deleted-posts [ds]
  (let [conn (get-conn ds)]
    (jdbc/execute! conn
      ["SELECT p.post_id, p.content, p.footnotes, p.image, p.created_at, latest.first_at
        FROM posts p
        INNER JOIN (
          SELECT post_id, MAX(created_at) AS max_created_at, MIN(created_at) AS first_at
          FROM posts
          GROUP BY post_id
        ) latest ON p.post_id = latest.post_id AND p.created_at = latest.max_created_at
        INNER JOIN post_meta pm ON pm.post_id = p.post_id AND pm.deleted = 1
        ORDER BY latest.first_at DESC"]
      jdbc-opts)))

(defn get-post [ds post-id {:keys [include-deleted?]}]
  (let [conn (get-conn ds)
        base [:and [:= :p.post_id post-id]]
        base (if include-deleted? base (conj base [:= :pm.deleted 0]))]
    (jdbc/execute-one! conn
      (sql/format {:select (mapv #(keyword (str "p." (name %))) post-cols)
                   :from [[:posts :p]]
                   :join [[:post_meta :pm] [:= :pm.post_id :p.post_id]]
                   :where base
                   :order-by [[:p.created_at :desc]]
                   :limit 1})
      jdbc-opts)))

(defn get-post-version [ds post-id as-of {:keys [include-deleted?]}]
  (let [conn (get-conn ds)
        base [:and [:= :p.post_id post-id] [:<= :p.created_at as-of]]
        base (if include-deleted? base (conj base [:= :pm.deleted 0]))]
    (jdbc/execute-one! conn
      (sql/format {:select (mapv #(keyword (str "p." (name %))) post-cols)
                   :from [[:posts :p]]
                   :join [[:post_meta :pm] [:= :pm.post_id :p.post_id]]
                   :where base
                   :order-by [[:p.created_at :desc]]
                   :limit 1})
      jdbc-opts)))

(defn get-post-versions [ds post-id {:keys [include-deleted?]}]
  (let [conn (get-conn ds)
        base [:and [:= :p.post_id post-id]]
        base (if include-deleted? base (conj base [:= :pm.deleted 0]))]
    (jdbc/execute! conn
      (sql/format {:select (mapv #(keyword (str "p." (name %))) post-cols)
                   :from [[:posts :p]]
                   :join [[:post_meta :pm] [:= :pm.post_id :p.post_id]]
                   :where base
                   :order-by [[:p.created_at :desc]]})
      jdbc-opts)))

(defn list-article-posts [ds]
  (let [conn (get-conn ds)]
    (jdbc/execute! conn
      ["SELECT p.post_id, p.content, p.footnotes, p.image, p.created_at, latest.first_at
        FROM posts p
        INNER JOIN (
          SELECT post_id, MAX(created_at) AS max_created_at, MIN(created_at) AS first_at
          FROM posts
          GROUP BY post_id
        ) latest ON p.post_id = latest.post_id AND p.created_at = latest.max_created_at
        INNER JOIN post_meta pm ON pm.post_id = p.post_id AND pm.deleted = 0
        INNER JOIN article_posts ap ON ap.post_id = p.post_id
        ORDER BY latest.first_at DESC"]
      jdbc-opts)))

(defn get-post-article-link [ds post-id]
  (let [conn (get-conn ds)]
    (jdbc/execute-one! conn
      (sql/format {:select [:ap.article_id :ap.article_version :a.title :a.subtitle :am.preview_image]
                   :from [[:article_posts :ap]]
                   :join [[:articles :a] [:and
                                          [:= :a.article_id :ap.article_id]
                                          [:= :a.version :ap.article_version]]
                          [:article_meta :am] [:= :am.article_id :ap.article_id]]
                   :where [:= :ap.post_id post-id]
                   :order-by [[:a.created_at :desc]]
                   :limit 1})
      jdbc-opts)))

(defn delete-article! [ds article-id]
  (let [conn (get-conn ds)]
    (jdbc/execute-one! conn
      (sql/format {:update :article_meta
                   :set {:deleted 1}
                   :where [:= :article_id article-id]}))))

(defn delete-post! [ds post-id]
  (let [conn (get-conn ds)]
    (jdbc/execute-one! conn
      (sql/format {:update :post_meta
                   :set {:deleted 1}
                   :where [:= :post_id post-id]}))))

(defn get-posts-article-links [ds post-ids]
  (if (empty? post-ids)
    {}
    (let [conn (get-conn ds)
          rows (jdbc/execute! conn
                 (sql/format {:select [:ap.post_id :ap.article_id :ap.article_version :a.title :a.subtitle :am.preview_image]
                              :from [[:article_posts :ap]]
                              :join [[:articles :a] [:and
                                                     [:= :a.article_id :ap.article_id]
                                                     [:= :a.version :ap.article_version]]
                                     [:article_meta :am] [:= :am.article_id :ap.article_id]]
                              :where [:in :ap.post_id post-ids]
                              :order-by [[:a.created_at :desc]]})
                 jdbc-opts)]
      (into {} (map (fn [r] [(:post_id r) r]) rows)))))

(defn get-articles-post-content [ds article-ids]
  (if (empty? article-ids)
    {}
    (let [conn (get-conn ds)]
      (->> (jdbc/execute! conn
             (sql/format {:select [:ap.article_id :p.content]
                          :from [[:article_posts :ap]]
                          :join [[:posts :p] [:= :p.post_id :ap.post_id]]
                          :where [:in :ap.article_id article-ids]
                          :order-by [[:p.created_at :desc]]})
             jdbc-opts)
           (reduce (fn [m r] (if (contains? m (:article_id r)) m (assoc m (:article_id r) (:content r)))) {})))))

(defn get-articles-latest-post-dates [ds article-ids]
  (if (empty? article-ids)
    {}
    (let [conn (get-conn ds)
          placeholders (str/join "," (repeat (count article-ids) "?"))
          sql-str (str "SELECT ap.article_id, ap.article_version, MIN(p.created_at) as published_at
                        FROM article_posts ap
                        INNER JOIN posts p ON p.post_id = ap.post_id
                        INNER JOIN (
                          SELECT article_id, MAX(article_version) as max_version
                          FROM article_posts
                          GROUP BY article_id
                        ) latest ON ap.article_id = latest.article_id AND ap.article_version = latest.max_version
                        WHERE ap.article_id IN (" placeholders ")
                        GROUP BY ap.article_id")]
      (->> (jdbc/execute! conn (into [sql-str] article-ids) jdbc-opts)
           (reduce (fn [m r] (assoc m (:article_id r) r)) {})))))
