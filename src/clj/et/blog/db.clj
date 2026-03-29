(ns et.blog.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
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

(defn next-article-id [ds]
  (let [conn (get-conn ds)
        result (jdbc/execute-one! conn
                 (sql/format {:select [[[:coalesce [:max :article_id] 0]]]
                              :from [:articles]})
                 jdbc-opts)]
    (inc (first (vals result)))))

(defn create-article! [ds {:keys [title subtitle content footnotes addenda publish?]}]
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
                             :version version}]})
      jdbc-opts)
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

(defn update-article! [ds article-id {:keys [title subtitle content footnotes addenda publish?]}]
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
                             :version version}]})
      jdbc-opts)))

(def ^:private article-cols [:article_id :title :subtitle :content :footnotes :addenda :created_at :version])

(defn list-articles [ds {:keys [published-only?]}]
  (let [conn (get-conn ds)]
    (if published-only?
      (jdbc/execute! conn
        ["SELECT a.article_id, a.title, a.subtitle, a.content, a.footnotes, a.addenda, a.created_at, a.version
          FROM articles a
          INNER JOIN (
            SELECT article_id, MAX(created_at) AS max_created_at
            FROM articles
            WHERE version > 0
            GROUP BY article_id
          ) latest ON a.article_id = latest.article_id AND a.created_at = latest.max_created_at
          ORDER BY a.created_at DESC"]
        jdbc-opts)
      (jdbc/execute! conn
        ["SELECT a.article_id, a.title, a.subtitle, a.content, a.footnotes, a.addenda, a.created_at, a.version
          FROM articles a
          INNER JOIN (
            SELECT article_id, MAX(created_at) AS max_created_at
            FROM articles
            GROUP BY article_id
          ) latest ON a.article_id = latest.article_id AND a.created_at = latest.max_created_at
          ORDER BY a.created_at DESC"]
        jdbc-opts))))

(defn get-article [ds article-id {:keys [published-only?]}]
  (let [conn (get-conn ds)]
    (jdbc/execute-one! conn
      (sql/format {:select article-cols
                   :from [:articles]
                   :where (if published-only?
                            [:and [:= :article_id article-id] [:> :version 0]]
                            [:= :article_id article-id])
                   :order-by [[:created_at :desc]]
                   :limit 1})
      jdbc-opts)))

(defn get-article-version [ds article-id as-of]
  (let [conn (get-conn ds)]
    (jdbc/execute-one! conn
      (sql/format {:select article-cols
                   :from [:articles]
                   :where [:and [:= :article_id article-id] [:<= :created_at as-of]]
                   :order-by [[:created_at :desc]]
                   :limit 1})
      jdbc-opts)))

(defn get-article-by-version [ds article-id version]
  (let [conn (get-conn ds)]
    (jdbc/execute-one! conn
      (sql/format {:select article-cols
                   :from [:articles]
                   :where [:and [:= :article_id article-id] [:= :version version]]
                   :order-by [[:created_at :desc]]
                   :limit 1})
      jdbc-opts)))

(defn get-article-versions [ds article-id {:keys [published-only?]}]
  (let [conn (get-conn ds)]
    (jdbc/execute! conn
      (sql/format {:select article-cols
                   :from [:articles]
                   :where (if published-only?
                            [:and [:= :article_id article-id] [:> :version 0]]
                            [:= :article_id article-id])
                   :order-by [[:created_at :desc]]})
      jdbc-opts)))

;; --- Posts ---

(def ^:private post-cols [:post_id :content :footnotes :created_at])

(defn next-post-id [ds]
  (let [conn (get-conn ds)
        result (jdbc/execute-one! conn
                 (sql/format {:select [[[:coalesce [:max :post_id] 0]]]
                              :from [:posts]})
                 jdbc-opts)]
    (inc (first (vals result)))))

(defn create-post! [ds {:keys [content footnotes]}]
  (let [conn (get-conn ds)
        post-id (next-post-id ds)]
    (jdbc/execute-one! conn
      (sql/format {:insert-into :posts
                   :values [{:post_id post-id
                             :content (or content "")
                             :footnotes (or footnotes "")}]})
      jdbc-opts)
    post-id))

(defn update-post! [ds post-id {:keys [content footnotes]}]
  (let [conn (get-conn ds)]
    (jdbc/execute-one! conn
      (sql/format {:insert-into :posts
                   :values [{:post_id post-id
                             :content (or content "")
                             :footnotes (or footnotes "")}]})
      jdbc-opts)))

(defn list-posts [ds]
  (let [conn (get-conn ds)]
    (jdbc/execute! conn
      ["SELECT p.post_id, p.content, p.footnotes, p.created_at, latest.first_at
        FROM posts p
        INNER JOIN (
          SELECT post_id, MAX(created_at) AS max_created_at, MIN(created_at) AS first_at
          FROM posts
          GROUP BY post_id
        ) latest ON p.post_id = latest.post_id AND p.created_at = latest.max_created_at
        ORDER BY latest.first_at DESC"]
      jdbc-opts)))

(defn get-post [ds post-id]
  (let [conn (get-conn ds)]
    (jdbc/execute-one! conn
      (sql/format {:select post-cols
                   :from [:posts]
                   :where [:= :post_id post-id]
                   :order-by [[:created_at :desc]]
                   :limit 1})
      jdbc-opts)))

(defn get-post-version [ds post-id as-of]
  (let [conn (get-conn ds)]
    (jdbc/execute-one! conn
      (sql/format {:select post-cols
                   :from [:posts]
                   :where [:and [:= :post_id post-id] [:<= :created_at as-of]]
                   :order-by [[:created_at :desc]]
                   :limit 1})
      jdbc-opts)))

(defn get-post-versions [ds post-id]
  (let [conn (get-conn ds)]
    (jdbc/execute! conn
      (sql/format {:select post-cols
                   :from [:posts]
                   :where [:= :post_id post-id]
                   :order-by [[:created_at :desc]]})
      jdbc-opts)))
