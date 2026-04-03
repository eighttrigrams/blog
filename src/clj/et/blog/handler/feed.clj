(ns et.blog.handler.feed
  (:require [et.blog.handler.common :as c]
            [et.blog.db :as db]
            [et.blog.feed :as feed]
            [et.blog.render :as render]))

(defn- build-feed [req title feed-path posts]
  (let [fetch-fn (fn [aid as-of] (db/get-article-version (c/ensure-ds) aid as-of {}))
        post-ids (mapv :post_id posts)
        article-links (db/get-posts-article-links (c/ensure-ds) post-ids)
        rendered (mapv #(render/render-content % fetch-fn) posts)
        base (c/site-url req)]
    {:status 200
     :headers {"Content-Type" "application/atom+xml; charset=utf-8"}
     :body (feed/atom-feed {:title title
                            :feed-url (str base feed-path)
                            :site-url base
                            :posts posts
                            :article-links article-links
                            :rendered-posts rendered})}))

(defn feed-posts-handler [req]
  (let [posts (db/list-posts (c/ensure-ds))]
    (build-feed req "Blog - Posts" "/feed/posts.xml" posts)))

(defn feed-articles-handler [req]
  (let [articles (db/list-published-article-versions (c/ensure-ds))
        post-contents (db/get-article-version-post-contents (c/ensure-ds))
        base (c/site-url req)]
    {:status 200
     :headers {"Content-Type" "application/atom+xml; charset=utf-8"}
     :body (feed/articles-feed {:title "Blog - Articles"
                                :feed-url (str base "/feed/articles.xml")
                                :site-url base
                                :articles articles
                                :post-contents post-contents})}))
