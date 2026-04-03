(ns et.blog.handler.comments
  (:require [et.blog.handler.common :as c]
            [et.blog.db :as db]
            [et.blog.views :as views]
            [et.blog.render :as render]
            [et.blog.mail :as mail]
            [et.blog.tracker :as tracker]
            [et.blog.middleware.circuit-breaker :as circuit-breaker]
            [clojure.string :as str]))

(defn comment-page-handler [req]
  (let [auth? (c/logged-in? req)
        article-id (Integer/parseInt (get-in req [:params :id]))
        ver (Integer/parseInt (get-in req [:params :version]))
        comment-id (Integer/parseInt (get-in req [:params :comment-id]))
        article (db/get-article-by-version (c/ensure-ds) article-id ver {})
        comment (db/get-comment (c/ensure-ds) comment-id)]
    (if (and article comment (= (:article_id comment) article-id) (= (:article_version comment) ver))
      (let [replies (db/get-replies-for-comment (c/ensure-ds) comment-id)]
        (c/html-response 200
          (views/comment-page {:article article :comment comment
                               :rendered-body (render/markdown->html (:body comment))
                               :replies replies
                               :logged-in? auth?})))
      (c/html-response 404
        (views/not-found-page {:logged-in? auth?})))))

(defn article-comments-handler [req]
  (let [auth? (c/logged-in? req)
        article-id (Integer/parseInt (get-in req [:params :id]))
        article (db/get-article (c/ensure-ds) article-id {:published-only? (not auth?)})]
    (if article
      (let [comments (db/get-comments-for-article (c/ensure-ds) article-id)
            replies-by-comment (when (seq comments)
                                 (group-by :comment_id
                                   (db/get-replies-for-comments (c/ensure-ds) (map :id comments))))
            comments (mapv #(assoc % :replies (get replies-by-comment (:id %))) comments)]
        (c/html-response 200
          (views/comments-list-page {:article article :comments comments :logged-in? auth?})))
      (c/html-response 404
        (views/not-found-page {:logged-in? auth?})))))

(defn version-comments-handler [req]
  (let [auth? (c/logged-in? req)
        article-id (Integer/parseInt (get-in req [:params :id]))
        ver (Integer/parseInt (get-in req [:params :version]))
        article (db/get-article-by-version (c/ensure-ds) article-id ver {})]
    (if (and article (pos? ver))
      (let [comments (db/get-comments-for-version (c/ensure-ds) article-id ver)
            replies-by-comment (when (seq comments)
                                 (group-by :comment_id
                                   (db/get-replies-for-comments (c/ensure-ds) (map :id comments))))
            comments (mapv #(assoc % :replies (get replies-by-comment (:id %))) comments)]
        (c/html-response 200
          (views/comments-list-page {:article article :comments comments :version ver :logged-in? auth?})))
      (c/html-response 404
        (views/not-found-page {:logged-in? auth?})))))

(defn comment-form-handler [req]
  (let [id (Integer/parseInt (get-in req [:params :id]))
        ver (Integer/parseInt (get-in req [:params :version]))
        article (db/get-article-by-version (c/ensure-ds) id ver {})]
    (if (and article (pos? ver))
      (c/html-response 200
        (views/comment-form-page {:article article :logged-in? (c/logged-in? req)}))
      (c/html-response 404
        (views/not-found-page {:logged-in? (c/logged-in? req)})))))

(defn comment-submit-handler [req]
  (let [id (Integer/parseInt (get-in req [:params :id]))
        ver (Integer/parseInt (get-in req [:params :version]))
        article (db/get-article-by-version (c/ensure-ds) id ver {})]
    (if (or (nil? article) (zero? ver))
      (c/html-response 404
        (views/not-found-page {:logged-in? (c/logged-in? req)}))
      (if-not (circuit-breaker/check-and-record!)
        (c/html-response 503
          (views/comment-form-page {:article article :logged-in? (c/logged-in? req)
                                    :error "This functionality is temporarily unavailable. Please try again later."}))
        (let [email (str/trim (or (get-in req [:form-params "email"]) ""))
              display-name (str/trim (or (get-in req [:form-params "display-name"]) ""))
              body (str/trim (or (get-in req [:form-params "body"]) ""))]
          (if (or (str/blank? email) (str/blank? display-name) (str/blank? body))
            (c/html-response 400
              (views/comment-form-page {:article article :logged-in? (c/logged-in? req)
                                        :error "All fields are required."}))
            (do
              (db/create-comment! (c/ensure-ds) id ver email display-name body)
              (future
                (try
                  (tracker/send-message!
                    (str "Blog comment on \"" (:title article) "\" (v" ver ")")
                    (str "From: " display-name " <" email ">\n\n" body)
                    "eighttrigrams.net")
                  (catch Exception e
                    (println "Failed to forward comment to tracker:" (.getMessage e)))))
              (c/redirect (str "/article/" id "/version/" ver)))))))))

(defn confirm-delete-comment-handler [req]
  (c/require-login req
    (fn [req]
      (let [id (Integer/parseInt (get-in req [:params :id]))
            comment (db/get-comment (c/ensure-ds) id)]
        (if comment
          (c/html-response 200
            (views/confirm-delete-comment-page {:comment comment :logged-in? true}))
          (c/html-response 404
            (views/not-found-page {:logged-in? true})))))))

(defn delete-comment-handler [req]
  (c/require-login req
    (fn [_]
      (let [id (Integer/parseInt (get-in req [:params :id]))
            comment (db/get-comment (c/ensure-ds) id)
            reason (str/trim (or (get-in req [:form-params "reason"]) ""))]
        (if comment
          (do
            (db/delete-comment! (c/ensure-ds) id)
            (future
              (try
                (let [article (db/get-article-by-version (c/ensure-ds) (:article_id comment) (:article_version comment) {})
                      article-title (or (:title article) (str "Article " (:article_id comment)))
                      body (str "Your comment on \"" article-title "\" (v" (:article_version comment) ") has been removed."
                                (when (not= reason "")
                                  (str "\n\nReason: " reason))
                                "\n\nYour comment was:\n\n" (:body comment))]
                  (mail/send-plain-email! (:email comment)
                    (str "Comment removed: " article-title)
                    body))
                (catch Exception e
                  (println "Failed to send comment deletion email:" (.getMessage e)))))
            (c/redirect (str "/article/" (:article_id comment) "/version/" (:article_version comment))))
          (c/html-response 404
            (views/not-found-page {:logged-in? true})))))))

(defn confirm-delete-reply-handler [req]
  (c/require-login req
    (fn [req]
      (let [id (Integer/parseInt (get-in req [:params :id]))
            reply (db/get-reply (c/ensure-ds) id)]
        (if reply
          (let [comment (db/get-comment (c/ensure-ds) (:comment_id reply))]
            (c/html-response 200
              (views/confirm-delete-reply-page {:reply reply :comment comment :logged-in? true})))
          (c/html-response 404
            (views/not-found-page {:logged-in? true})))))))

(defn delete-reply-handler [req]
  (c/require-login req
    (fn [_]
      (let [id (Integer/parseInt (get-in req [:params :id]))
            reply (db/get-reply (c/ensure-ds) id)
            reason (str/trim (or (get-in req [:form-params "reason"]) ""))]
        (if reply
          (let [comment (db/get-comment (c/ensure-ds) (:comment_id reply))]
            (db/delete-reply! (c/ensure-ds) id)
            (future
              (try
                (let [article (db/get-article-by-version (c/ensure-ds) (:article_id comment) (:article_version comment) {})
                      article-title (or (:title article) (str "Article " (:article_id comment)))
                      body (str "Your reply on \"" article-title "\" has been removed."
                                (when (not= reason "")
                                  (str "\n\nReason: " reason))
                                "\n\nYour reply was:\n\n" (:body reply))]
                  (mail/send-plain-email! (:email reply)
                    (str "Reply removed: " article-title)
                    body))
                (catch Exception e
                  (println "Failed to send reply deletion email:" (.getMessage e)))))
            (c/redirect (str "/article/" (:article_id comment) "/version/" (:article_version comment)
                           "/comment/" (:comment_id reply))))
          (c/html-response 404
            (views/not-found-page {:logged-in? true})))))))

(defn reply-form-handler [req]
  (let [comment-id (Integer/parseInt (get-in req [:params :id]))
        comment (db/get-comment (c/ensure-ds) comment-id)]
    (if comment
      (let [article (db/get-article-by-version (c/ensure-ds) (:article_id comment) (:article_version comment) {})]
        (if article
          (c/html-response 200
            (views/reply-form-page {:comment comment :article article :logged-in? (c/logged-in? req)}))
          (c/html-response 404
            (views/not-found-page {:logged-in? (c/logged-in? req)}))))
      (c/html-response 404
        (views/not-found-page {:logged-in? (c/logged-in? req)})))))

(defn reply-submit-handler [req]
  (let [comment-id (Integer/parseInt (get-in req [:params :id]))
        comment (db/get-comment (c/ensure-ds) comment-id)]
    (if (nil? comment)
      (c/html-response 404
        (views/not-found-page {:logged-in? (c/logged-in? req)}))
      (let [article (db/get-article-by-version (c/ensure-ds) (:article_id comment) (:article_version comment) {})]
        (if (nil? article)
          (c/html-response 404
            (views/not-found-page {:logged-in? (c/logged-in? req)}))
          (if-not (circuit-breaker/check-and-record!)
            (c/html-response 503
              (views/reply-form-page {:comment comment :article article :logged-in? (c/logged-in? req)
                                      :error "This functionality is temporarily unavailable. Please try again later."}))
            (let [email (str/trim (or (get-in req [:form-params "email"]) ""))
                  display-name (str/trim (or (get-in req [:form-params "display-name"]) ""))
                  body (str/trim (or (get-in req [:form-params "body"]) ""))]
              (if (or (str/blank? email) (str/blank? display-name) (str/blank? body))
                (c/html-response 400
                  (views/reply-form-page {:comment comment :article article :logged-in? (c/logged-in? req)
                                          :error "All fields are required."}))
                (do
                  (db/create-reply! (c/ensure-ds) comment-id email display-name body)
                  (future
                    (try
                      (tracker/send-message!
                        (str "Blog reply on \"" (:title article) "\" to " (:display_name comment))
                        (str "From: " display-name " <" email ">\n\n" body)
                        "eighttrigrams.net")
                      (catch Exception e
                        (println "Failed to forward reply to tracker:" (.getMessage e)))))
                  (c/redirect (str "/article/" (:article_id comment) "/version/" (:article_version comment)
                                   "/comment/" comment-id)))))))))))
