(ns et.blog.mail
  (:require [postal.core :as postal]
            [et.blog.render :as render]))

(defn- smtp-config []
  {:host (or (System/getenv "SMTP_HOST") "w00cbd36.kasserver.com")
   :port 587
   :tls true
   :user (System/getenv "SMTP_USER")
   :pass (System/getenv "SMTP_PASSWORD")})

(defn- build-html [title subtitle post-content article-url]
  (str "<h1>" title "</h1>"
       (when (and subtitle (not= subtitle ""))
         (str "<p><em>" subtitle "</em></p>"))
       (render/markdown->html post-content)
       "<p><a href=\"" article-url "\">" article-url "</a></p>"))

(defn send-article-notification! [subscribers title subtitle post-content article-url]
  (let [config (smtp-config)]
    (when (and (:user config) (:pass config) (seq subscribers))
      (doseq [{:keys [email]} subscribers]
        (try
          (postal/send-message config
            {:from "dan@eighttrigrams.net"
             :to email
             :subject (str "New article: " title)
             :body [{:type "text/html; charset=utf-8"
                      :content (build-html title subtitle post-content article-url)}]})
          (println (str "Sent notification to " email))
          (catch Exception e
            (println (str "Failed to send to " email ": " (.getMessage e)))))))))
