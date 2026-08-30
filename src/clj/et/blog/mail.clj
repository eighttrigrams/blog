(ns et.blog.mail
  "Outbound mail: subscriber notifications for new articles, and the plain
  notice a commenter gets when their comment is removed.

  Credentials arrive through `configure!`, called from `server/build-handler`
  alongside `tracker/configure!`. They used to come from SMTP_HOST / SMTP_USER
  / SMTP_PASSWORD via `System/getenv`, and nothing ever set those on the
  deployed machine — `fly secrets` carries four names and none of them is an
  SMTP one. Both senders below then answered a missing credential with `nil`
  rather than an exception, so the `try/catch` around the deletion notice
  printed nothing and no article notification was ever sent to anybody on the
  subscriber list. Same silent shape that kept blog out of the tracker inbox;
  hence the error logs here."
  (:require [postal.core :as postal]
            [taoensso.telemere :as tel]
            [et.blog.render :as render]
            [clojure.string :as str]))

(defonce ^:private *config (atom nil))

(defn configure!
  "Set the SMTP credentials: {:host :user :password} and an optional :port.
  Called from server/build-handler with (:smtp config); nil or partial
  disables sending, which is the normal case in dev."
  [cfg]
  (reset! *config cfg))

(defn- smtp-config []
  (let [{:keys [host port user password]} @*config]
    (when (and host user password)
      {:host host
       :port (or port 587)
       :tls true
       :user user
       :pass password})))

(defn- unconfigured! [what]
  (tel/log! :error
    (str "SMTP is not configured — dropping " what ". "
         "Set :smtp {:host .. :user .. :password ..} under :apps :blog "
         "in the umbrella config.")))

(defn- downshift-headings [html]
  (str/replace html #"<(/?)h([1-6])"
    (fn [[_ slash level]]
      (let [n (min 6 (+ (Integer/parseInt level) 2))]
        (str "<" slash "h" n)))))

(defn- build-html [title subtitle post-content article-url]
  (str "<h1>" title "</h1>"
       (when (and subtitle (not= subtitle ""))
         (str "<h2>" subtitle "</h2>"))
       (downshift-headings (or (render/markdown->html post-content) ""))
       "<p><a href=\"" article-url "\">" article-url "</a></p>"))

(defn send-article-notification! [subscribers title subtitle post-content article-url]
  (if-let [config (smtp-config)]
    ;; An empty subscriber list is not a failure and says nothing.
    (when (seq subscribers)
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
            (println (str "Failed to send to " email ": " (.getMessage e)))))))
    (when (seq subscribers)
      (unconfigured! (str "an article notification to " (count subscribers)
                          " subscriber(s): \"" title "\"")))))

(defn send-plain-email! [to subject body]
  (if-let [config (smtp-config)]
    (postal/send-message config
      {:from "dan@eighttrigrams.net"
       :to to
       :subject subject
       :body body})
    (unconfigured! (str "an email to " to " (\"" subject "\")"))))
