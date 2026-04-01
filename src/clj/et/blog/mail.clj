(ns et.blog.mail
  (:require [postal.core :as postal]
            [com.taoensso.telemere :as tel]))

(defn- smtp-config []
  {:host (or (System/getenv "SMTP_HOST") "w00cbd36.kasserver.com")
   :port 587
   :tls true
   :user (System/getenv "SMTP_USER")
   :pass (System/getenv "SMTP_PASSWORD")})

(defn send-article-notification! [subscribers title article-url]
  (let [config (smtp-config)]
    (when (and (:user config) (:pass config) (seq subscribers))
      (doseq [{:keys [email]} subscribers]
        (try
          (postal/send-message config
            {:from "dan@eighttrigrams.net"
             :to email
             :subject (str "New article: " title)
             :body (str "A new article has been published: " title "\n\n" article-url)})
          (tel/log! :info (str "Sent notification to " email))
          (catch Exception e
            (tel/log! :error (str "Failed to send to " email ": " (.getMessage e)))))))))
