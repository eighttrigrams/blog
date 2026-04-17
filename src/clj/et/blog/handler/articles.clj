(ns et.blog.handler.articles
  (:require [et.blog.handler.common :as c]
            [et.blog.db :as db]
            [et.blog.views :as views]
            [et.blog.render :as render]
            [et.blog.mail :as mail]
            [clojure.string :as str]))

(defn home-handler [req]
  (let [auth? (c/logged-in? req)
        topic (get-in req [:query-params "topic"])
        articles (db/list-articles (c/ensure-ds) {:published-only? (not auth?)})
        article-ids (mapv :article_id articles)
        post-dates (db/get-articles-latest-post-dates (c/ensure-ds) article-ids)
        articles (->> articles
                      (mapv #(let [pd (get post-dates (:article_id %))]
                               (-> %
                                   c/resolve-preview-image
                                   (assoc :latest-version (:article_version pd))
                                   (assoc :latest-published-at (:published_at pd)))))
                      (sort-by :latest-published-at #(compare %2 %1)))
        articles (if auth? articles (remove #(= 36 (:article_id %)) articles))
        articles (if topic
                   (filter #(some #{(str/lower-case topic)} (map str/lower-case (str/split (or (:topics %) "") #"\s+"))) articles)
                   articles)]
    (c/html-response 200
      (views/home-page {:articles articles :logged-in? auth? :topic topic}))))

(defn article-handler [req]
  (let [auth? (c/logged-in? req)
        pub? (not auth?)
        id (Integer/parseInt (get-in req [:params :id]))
        as-of (get-in req [:params :as-of])
        ver (get-in req [:params :version])
        raw     (cond
                  as-of (db/get-article-version (c/ensure-ds) id as-of {})
                  ver   (let [v (Integer/parseInt ver)]
                          (if (and pub? (zero? v))
                            nil
                            (db/get-article-by-version (c/ensure-ds) id v {})))
                  :else (db/get-article (c/ensure-ds) id {:published-only? pub?}))
        article (if (and pub? raw (zero? (or (:version raw) 0)))
                  nil
                  raw)]
    (if article
      (let [versions (db/get-article-versions (c/ensure-ds) id {:published-only? pub?})
            fetch-fn (fn [aid as-of] (db/get-article-version (c/ensure-ds) aid as-of {}))
            rendered-content (render/render-content article fetch-fn)
            rendered-addenda (render/markdown->html (:addenda article))
            rendered-preamble (render/markdown->html (:preamble article))
            comments (when (and (:version article) (pos? (:version article)))
                       (db/get-comments-up-to-version (c/ensure-ds) id (:version article)))
            replies-by-comment (when (seq comments)
                                 (group-by :comment_id
                                   (db/get-replies-for-comments (c/ensure-ds) (map :id comments))))
            comments-with-replies (when comments
                                    (mapv #(assoc % :replies (get replies-by-comment (:id %))) comments))]
        (c/html-response 200
          (views/article-page {:article article :versions versions :logged-in? auth?
                               :current-version (:created_at article)
                               :rendered-content rendered-content
                               :rendered-addenda rendered-addenda
                               :rendered-preamble rendered-preamble
                               :comments comments-with-replies})))
      (c/html-response 404
        (views/not-found-page {:logged-in? auth?})))))

(defn drafts-handler [req]
  (c/require-login req
    (fn [req]
      (let [articles (db/list-draft-articles (c/ensure-ds))]
        (c/html-response 200
          (views/drafts-page {:articles articles :logged-in? true}))))))

(defn new-article-handler [req]
  (c/require-login req
    (fn [req]
      (c/html-response 200
        (views/edit-page {:new? true :logged-in? (c/logged-in? req)})))))

(defn create-article-handler [req]
  (c/require-login req
    (fn [_]
      (let [title (str/trim (or (get-in req [:form-params "title"]) ""))
            subtitle (or (get-in req [:form-params "subtitle"]) "")
            content (or (get-in req [:form-params "content"]) "")
            footnotes (or (get-in req [:form-params "footnotes"]) "")
            addenda (or (get-in req [:form-params "addenda"]) "")
            preamble (or (get-in req [:form-params "preamble"]) "")
            preview-image (or (get-in req [:form-params "preview-image"]) "")
            abstract (or (get-in req [:form-params "abstract"]) "")
            topics (or (get-in req [:form-params "topics"]) "")
            post-content (str/trim (or (get-in req [:form-params "post-content"]) ""))
            publish? (some? (get-in req [:form-params "publish"]))
            article {:title title :subtitle subtitle :content content :footnotes footnotes :addenda addenda :preamble preamble :preview-image preview-image :abstract abstract :topics topics}]
        (cond
          (str/blank? title)
          (c/html-response 400
            (views/edit-page {:new? true :logged-in? true :article article}))

          (and publish? (str/blank? post-content))
          (c/html-response 400
            (views/edit-page {:new? true :logged-in? true :article article
                              :error "Post content is required when publishing."
                              :post-content post-content}))

          :else
          (let [article-id (db/create-article! (c/ensure-ds) (merge article {:publish? publish?
                                                                              :post-content (when publish? post-content)}))]
            (when publish?
              (future
                (let [subscribers (db/list-email-subscribers (c/ensure-ds))
                      base (c/site-url req)]
                  (mail/send-article-notification! subscribers title subtitle post-content (str base "/article/" article-id)))))
            (c/redirect (str "/article/" article-id))))))))

(defn edit-article-handler [req]
  (c/require-login req
    (fn [req]
      (let [id (Integer/parseInt (get-in req [:params :id]))
            article (db/get-article (c/ensure-ds) id {})]
        (if article
          (c/html-response 200
            (views/edit-page {:article article :logged-in? true}))
          (c/html-response 404
            (views/not-found-page {:logged-in? true})))))))

(defn update-article-handler [req]
  (c/require-login req
    (fn [_]
      (let [id (Integer/parseInt (get-in req [:params :id]))
            title (str/trim (or (get-in req [:form-params "title"]) ""))
            subtitle (or (get-in req [:form-params "subtitle"]) "")
            content (or (get-in req [:form-params "content"]) "")
            footnotes (or (get-in req [:form-params "footnotes"]) "")
            addenda (or (get-in req [:form-params "addenda"]) "")
            preamble (or (get-in req [:form-params "preamble"]) "")
            preview-image (or (get-in req [:form-params "preview-image"]) "")
            abstract (or (get-in req [:form-params "abstract"]) "")
            topics (or (get-in req [:form-params "topics"]) "")
            post-content (str/trim (or (get-in req [:form-params "post-content"]) ""))
            publish? (some? (get-in req [:form-params "publish"]))
            skip-post? (= id 36)
            article {:article_id id :title title :subtitle subtitle :content content :footnotes footnotes :addenda addenda :preamble preamble :preview-image preview-image :abstract abstract :topics topics}]
        (cond
          (str/blank? title)
          (c/html-response 400
            (views/edit-page {:article article :logged-in? true}))

          (and publish? (not skip-post?) (str/blank? post-content))
          (c/html-response 400
            (views/edit-page {:article article :logged-in? true
                              :error "Post content is required when publishing."
                              :post-content post-content}))

          :else
          (do
            (db/update-article! (c/ensure-ds) id (merge article {:publish? publish?
                                                                  :post-content (when (and publish? (not skip-post?)) post-content)}))
            (when (and publish? (not skip-post?))
              (future
                (let [subscribers (db/list-email-subscribers (c/ensure-ds))
                      base (c/site-url req)]
                  (mail/send-article-notification! subscribers title subtitle post-content (str base "/article/" id)))))
            (c/redirect (str "/article/" id))))))))

(defn confirm-delete-article-handler [req]
  (c/require-login req
    (fn [req]
      (let [id (Integer/parseInt (get-in req [:params :id]))
            article (db/get-article (c/ensure-ds) id {})]
        (if article
          (c/html-response 200
            (views/confirm-delete-article-page {:article article :logged-in? true}))
          (c/html-response 404
            (views/not-found-page {:logged-in? true})))))))

(defn delete-article-handler [req]
  (c/require-login req
    (fn [_]
      (let [id (Integer/parseInt (get-in req [:params :id]))]
        (db/delete-article! (c/ensure-ds) id)
        (c/redirect "/")))))

(defn deleted-articles-handler [req]
  (c/require-login req
    (fn [_]
      (let [articles (db/list-deleted-articles (c/ensure-ds))]
        (c/html-response 200
          (views/deleted-articles-page {:articles articles :logged-in? true}))))))
