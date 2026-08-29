(ns et.blog.handler.email
  (:require [et.blog.handler.common :as c]
            [et.blog.db :as db]
            [et.blog.views :as views]
            [et.blog.tracker :as tracker]
            [et.blog.middleware.circuit-breaker :as circuit-breaker]
            [clojure.string :as str]))

(def ^:private unavailable-notice "This functionality is temporarily unavailable. Please try again later.")

(defn- page-data [req opts]
  (let [auth? (c/logged-in? req)]
    (merge {:logged-in? auth?
            :messages (when auth? (db/list-messages (c/ensure-ds)))
            :subscribers (when auth? (db/list-email-subscribers (c/ensure-ds)))}
           opts)))

(defn email-page-handler [req]
  (c/html-response 200
    (views/email-page (page-data req {}))))

(defn email-submit-handler [req]
  (if-not (circuit-breaker/check-and-record!)
    (c/html-response 503
      (views/email-page (page-data req {:error unavailable-notice})))
    (let [email (str/trim (or (get-in req [:form-params "email"]) ""))
          action (get-in req [:form-params "action"])]
      (if (str/blank? email)
        (c/html-response 400
          (views/email-page (page-data req {:notice "Please enter an email address."})))
        (let [unsubscribe? (= action "unsubscribe")]
          (if unsubscribe?
            (db/unsubscribe-email! (c/ensure-ds) email)
            (db/subscribe-email! (c/ensure-ds) email))
          ;; Subscriptions were the one event that never told anyone: this
          ;; handler wrote the row and rendered the page, and only
          ;; message-submit-handler below ever reached tracker.
          (future
            (try
              (tracker/send-message!
                (str "Blog " (if unsubscribe? "unsubscribe" "subscription") " from \"" email "\"")
                (str email (if unsubscribe? " unsubscribed from" " subscribed to") " the blog.")
                "eighttrigrams.net")
              (catch Exception e
                (println "Failed to forward subscription to tracker:" (.getMessage e)))))
          (c/html-response 200
            (views/email-page (page-data req {:notice (if unsubscribe?
                                                        "You have been unsubscribed."
                                                        "Thanks for subscribing!")}))))))))

(defn message-submit-handler [req]
  (if-not (circuit-breaker/check-and-record!)
    (c/html-response 503
      (views/email-page (page-data req {:error unavailable-notice})))
    (let [email (str/trim (or (get-in req [:form-params "email"]) ""))
          message (str/trim (or (get-in req [:form-params "message"]) ""))]
      (if (or (str/blank? email) (str/blank? message))
        (c/html-response 400
          (views/email-page (page-data req {:notice "Please fill in all fields."})))
        (do
          (db/create-message! (c/ensure-ds) email message)
          (future
            (try
              (tracker/send-message! (str "Blog message from \"" email "\"") message "eighttrigrams.net")
              (catch Exception e
                (println "Failed to forward to tracker:" (.getMessage e)))))
          (c/html-response 200
            (views/email-page (page-data req {:notice "Message sent!"}))))))))
