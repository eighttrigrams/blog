(ns et.blog.handler.posts
  (:require [et.blog.handler.common :as c]
            [et.blog.db :as db]
            [et.blog.views :as views]
            [et.blog.render :as render]
            [et.blog.images :as images]
            [cheshire.core :as json]
            [taoensso.telemere :as tel]
            [clojure.string :as str]))

(defn- resolve-link-preview [link]
  (c/resolve-image-field link :preview_image))

(defn posts-handler [req]
  (let [auth? (c/logged-in? req)
        fetch-fn (fn [aid as-of] (db/get-article-version (c/ensure-ds) aid as-of {}))
        posts (if auth?
                (db/list-posts (c/ensure-ds))
                (db/list-posts-published (c/ensure-ds)))
        post-ids (mapv :post_id posts)
        article-links (db/get-posts-article-links (c/ensure-ds) post-ids)
        posts (->> posts
                   (mapv (fn [post]
                           (let [{:keys [above-html below-html truncated?]} (render/render-content-preview post fetch-fn)]
                             (assoc post
                               :above-html above-html
                               :below-html below-html
                               :truncated? truncated?
                               :article-link (some-> (get article-links (:post_id post)) resolve-link-preview)
                               :resolved-image (not-empty (:image (c/resolve-image-field post :image))))))))]
    (c/html-response 200
      (views/posts-page {:posts posts :logged-in? auth?}))))

(defn post-handler [req]
  (let [auth? (c/logged-in? req)
        id (Integer/parseInt (get-in req [:params :id]))
        as-of (get-in req [:params :as-of])
        opts {:published-only? (not auth?)}
        post (cond
               (and as-of auth?) (db/get-post-version (c/ensure-ds) id as-of opts)
               :else (db/get-post (c/ensure-ds) id opts))]
    (if post
      (let [versions (if auth? (db/get-post-versions (c/ensure-ds) id {}) [post])
            fetch-fn (fn [aid as-of] (db/get-article-version (c/ensure-ds) aid as-of {}))
            rendered-content (render/render-content post fetch-fn)
            article-link (some-> (db/get-post-article-link (c/ensure-ds) id) resolve-link-preview)
            stats (db/get-post-publish-stats (c/ensure-ds) id)]
        (c/html-response 200
          (views/post-page {:post post :versions versions :logged-in? auth?
                            :current-version (:created_at post)
                            :rendered-content rendered-content
                            :article-link article-link
                            :resolved-image (not-empty (:image (c/resolve-image-field post :image)))
                            :first-published-at (:first_published_at stats)
                            :last-published-at (:last_published_at stats)
                            :published-count (or (:published_count stats) 0)})))
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
            publish? (some? (get-in req [:form-params "publish"]))
            post-id (db/create-post! (c/ensure-ds) {:content content :footnotes footnotes :image image :publish? publish?})]
        (c/redirect (str "/post/" post-id))))))

(defn edit-post-handler [req]
  (c/require-login req
    (fn [req]
      (let [id (Integer/parseInt (get-in req [:params :id]))
            post (db/get-post (c/ensure-ds) id {})
            stats (db/get-post-publish-stats (c/ensure-ds) id)]
        (if post
          (c/html-response 200
            (views/edit-post-page {:post post :logged-in? true
                                   :has-published? (pos? (or (:published_count stats) 0))}))
          (c/html-response 404
            (views/not-found-page {:logged-in? true})))))))

(defn update-post-handler [req]
  (c/require-login req
    (fn [_]
      (let [id (Integer/parseInt (get-in req [:params :id]))
            content (or (get-in req [:form-params "content"]) "")
            footnotes (or (get-in req [:form-params "footnotes"]) "")
            image (or (get-in req [:form-params "image"]) "")
            publish? (some? (get-in req [:form-params "publish"]))]
        (if publish?
          (db/publish-post! (c/ensure-ds) id {:content content :footnotes footnotes :image image})
          (db/update-post! (c/ensure-ds) id {:content content :footnotes footnotes :image image}))
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

;; --- the post image upload ---------------------------------------------
;;
;; The file arrives as the raw request body with its name in the query string,
;; not as multipart. ring-core 1.9.6's multipart middleware needs the servlet API,
;; which is not on this classpath, and bumping ring under a working jetty adapter
;; to post one file is a trade nobody asked for. A single file needs no envelope.
;;
;; Answers JSON rather than redirecting, because the caller is the edit page and a
;; navigation would take unsaved content with it.

(defn- json-response [status body]
  {:status status
   :headers {"Content-Type" "application/json; charset=utf-8"}
   :body (json/generate-string body)})

(defn- read-capped
  "The body as bytes, or nil once it goes past the cap. Read rather than trusted:
  Content-Length is whatever the client claimed."
  [^java.io.InputStream in cap]
  (let [out (java.io.ByteArrayOutputStream.)
        buf (byte-array 65536)]
    (loop []
      (let [n (.read in buf)]
        (cond
          (neg? n) (.toByteArray out)
          (> (+ (.size out) n) cap) nil
          :else (do (.write out buf 0 n) (recur)))))))

(defn upload-post-image-handler [req]
  (c/require-login req
    (fn [req]
      ;; A string key for the query parameter: wrap-params keywordizes nothing,
      ;; and compojure only keywordizes the route params. :id is a keyword here,
      ;; "filename" is not.
      (let [filename (get-in req [:query-params "filename"])
            ;; Parsed, not passed through. The post id is now part of the remote
            ;; directory, so a value straight off the URL would be a way to name
            ;; one - an integer cannot be.
            post-id (try (Integer/parseInt (get-in req [:params :id]))
                         (catch Exception _ nil))]
        (cond
          (nil? post-id)
          (json-response 400 {:error "Not a post id."})

          ;; No point creating a directory for a post that does not exist, and
          ;; it keeps the webspace free of folders for ids nobody ever had.
          (nil? (db/get-post (c/ensure-ds) post-id {:include-deleted? true}))
          (json-response 404 {:error "No such post."})

          (str/blank? (str filename))
          (json-response 400 {:error "No filename was sent."})

          ;; The extension decides, not a client-supplied content type: the
          ;; latter is whatever the caller felt like claiming, and the former is
          ;; also what the webspace will serve the file back as.
          (not (images/allowed-extension? filename))
          (json-response 415 {:error (str "Not an image this accepts: " filename)})

          (not (images/configured?))
          (json-response 503 {:error "Image upload is not configured on this server."})

          :else
          (if-let [bytes (read-capped (:body req) images/max-bytes)]
            (if (zero? (alength bytes))
              (json-response 400 {:error "The file was empty."})
              (try
                (with-open [in (java.io.ByteArrayInputStream. bytes)]
                  (json-response 200 {:path (images/upload! in filename post-id)}))
                (catch Exception e
                  ;; Log it as well as answering with it. The first time this
                  ;; failed in production `fly logs` had nothing to say, because
                  ;; the only copy of the reason went to the browser - which is no
                  ;; use at all when the person who can read the screen and the
                  ;; person debugging are not in the same place.
                  (tel/log! :error (str "Post image upload failed for "
                                        (pr-str filename) ": " (.getMessage e)
                                        (when-let [d (ex-data e)] (str " " (pr-str d)))))
                  (json-response 502 {:error (str "Upload failed: " (.getMessage e))}))))
            (json-response 413 {:error (str "Too large. The limit is "
                                            (quot images/max-bytes (* 1024 1024)) " MB.")})))))))
