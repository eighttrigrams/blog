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

(defn create-article! [ds title content]
  (let [conn (get-conn ds)
        article-id (next-article-id ds)]
    (jdbc/execute-one! conn
      (sql/format {:insert-into :articles
                   :values [{:article_id article-id
                             :title title
                             :content content}]})
      jdbc-opts)
    article-id))

(defn update-article! [ds article-id title content]
  (let [conn (get-conn ds)]
    (jdbc/execute-one! conn
      (sql/format {:insert-into :articles
                   :values [{:article_id article-id
                             :title title
                             :content content}]})
      jdbc-opts)))

(defn list-articles [ds]
  (let [conn (get-conn ds)]
    (jdbc/execute! conn
      ["SELECT a.article_id, a.title, a.content, a.created_at
        FROM articles a
        INNER JOIN (
          SELECT article_id, MAX(created_at) AS max_created_at
          FROM articles
          GROUP BY article_id
        ) latest ON a.article_id = latest.article_id AND a.created_at = latest.max_created_at
        ORDER BY a.created_at DESC"]
      jdbc-opts)))

(defn get-article [ds article-id]
  (let [conn (get-conn ds)]
    (jdbc/execute-one! conn
      (sql/format {:select [:article_id :title :content :created_at]
                   :from [:articles]
                   :where [:= :article_id article-id]
                   :order-by [[:created_at :desc]]
                   :limit 1})
      jdbc-opts)))

(defn get-article-version [ds article-id created-at]
  (let [conn (get-conn ds)]
    (jdbc/execute-one! conn
      (sql/format {:select [:article_id :title :content :created_at]
                   :from [:articles]
                   :where [:and [:= :article_id article-id] [:= :created_at created-at]]})
      jdbc-opts)))

(defn get-article-versions [ds article-id]
  (let [conn (get-conn ds)]
    (jdbc/execute! conn
      (sql/format {:select [:article_id :title :content :created_at]
                   :from [:articles]
                   :where [:= :article_id article-id]
                   :order-by [[:created_at :desc]]})
      jdbc-opts)))
