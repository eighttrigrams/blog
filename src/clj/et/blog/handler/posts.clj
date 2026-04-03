(ns et.blog.handler.posts
  (:require [et.blog.handler.common :as c]
            [et.blog.db :as db]
            [et.blog.views :as views]
            [et.blog.render :as render]))

(defn- resolve-link-preview [link]
  (c/resolve-image-field link :preview_image))

(defn posts-handler [req]
  (let [auth? (c/logged-in? req)
        fetch-fn (fn [aid as-of] (db/get-article-version (c/ensure-ds) aid as-of {}))
        posts (db/list-posts (c/ensure-ds))
        post-ids (mapv :post_id posts)
        article-links (db/get-posts-article-links (c/ensure-ds) post-ids)
        posts (->> posts
                   (mapv #(-> %
                              (assoc :rendered-content (render/render-content % fetch-fn)
                                     :article-link (some-> (get article-links (:post_id %)) resolve-link-preview)
                                     :resolved-image (not-empty (:image (c/resolve-image-field % :image)))))))]
    (c/html-response 200
      (views/posts-page {:posts posts :logged-in? auth?}))))

(defn post-handler [req]
  (let [auth? (c/logged-in? req)
        id (Integer/parseInt (get-in req [:params :id]))
        as-of (get-in req [:params :as-of])
        post (if as-of
               (db/get-post-version (c/ensure-ds) id as-of {})
               (db/get-post (c/ensure-ds) id {}))]
    (if post
      (let [versions (if auth? (db/get-post-versions (c/ensure-ds) id {}) [post])
            fetch-fn (fn [aid as-of] (db/get-article-version (c/ensure-ds) aid as-of {}))
            rendered-content (render/render-content post fetch-fn)
            article-link (some-> (db/get-post-article-link (c/ensure-ds) id) resolve-link-preview)]
        (c/html-response 200
          (views/post-page {:post post :versions versions :logged-in? auth?
                            :current-version (:created_at post)
                            :rendered-content rendered-content
                            :article-link article-link
                            :resolved-image (not-empty (:image (c/resolve-image-field post :image)))})))
      (c/html-response 404
        (views/not-found-page {:logged-in? auth?})))))

(defn new-post-handler [req]
  (c/require-login req
    (fn [req]
      (c/html-response 200
        (views/edit-post-page {:new? true :logged-in? (c/logged-in? req)})))))

(defn create-post-handler [req]
  (c/require-login req
    (fn [_]
      (let [content (or (get-in req [:form-params "content"]) "")
            footnotes (or (get-in req [:form-params "footnotes"]) "")
            image (or (get-in req [:form-params "image"]) "")
            post-id (db/create-post! (c/ensure-ds) {:content content :footnotes footnotes :image image})]
        (c/redirect (str "/post/" post-id))))))

(defn edit-post-handler [req]
  (c/require-login req
    (fn [req]
      (let [id (Integer/parseInt (get-in req [:params :id]))
            post (db/get-post (c/ensure-ds) id {})]
        (if post
          (c/html-response 200
            (views/edit-post-page {:post post :logged-in? true}))
          (c/html-response 404
            (views/not-found-page {:logged-in? true})))))))

(defn update-post-handler [req]
  (c/require-login req
    (fn [_]
      (let [id (Integer/parseInt (get-in req [:params :id]))
            content (or (get-in req [:form-params "content"]) "")
            footnotes (or (get-in req [:form-params "footnotes"]) "")
            image (or (get-in req [:form-params "image"]) "")]
        (db/update-post! (c/ensure-ds) id {:content content :footnotes footnotes :image image})
        (c/redirect (str "/post/" id))))))

(defn confirm-delete-post-handler [req]
  (c/require-login req
    (fn [req]
      (let [id (Integer/parseInt (get-in req [:params :id]))
            post (db/get-post (c/ensure-ds) id {})]
        (if post
          (c/html-response 200
            (views/confirm-delete-post-page {:post post :logged-in? true}))
          (c/html-response 404
            (views/not-found-page {:logged-in? true})))))))

(defn delete-post-handler [req]
  (c/require-login req
    (fn [_]
      (let [id (Integer/parseInt (get-in req [:params :id]))]
        (db/delete-post! (c/ensure-ds) id)
        (c/redirect "/posts")))))

(defn deleted-posts-handler [req]
  (c/require-login req
    (fn [_]
      (let [fetch-fn (fn [aid as-of] (db/get-article-version (c/ensure-ds) aid as-of {}))
            posts (db/list-deleted-posts (c/ensure-ds))
            post-ids (mapv :post_id posts)
            article-links (db/get-posts-article-links (c/ensure-ds) post-ids)
            posts (->> posts
                       (mapv #(assoc %
                                :rendered-content (render/render-content % fetch-fn)
                                :article-link (get article-links (:post_id %)))))]
        (c/html-response 200
          (views/deleted-posts-page {:posts posts :logged-in? true}))))))
