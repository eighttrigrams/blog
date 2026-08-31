(ns et.blog.handler.dashboard
  "The owner's dashboard: the interactivity switch, notes users, subscribers,
  and the event log that makes a missed notification mail recoverable.

  Notes users used to have a page to themselves; the handlers for creating and
  revoking one still live in et.blog.handler.notes-users and now render or
  redirect here."
  (:require [et.blog.handler.common :as c]
            [et.blog.db :as db]
            [et.blog.mail :as mail]
            [et.blog.views :as views]
            [clojure.string :as str]))

(defn page-data [opts]
  (merge {:logged-in? true
          :notes-users (db/list-notes-users (c/ensure-ds))
          :subscribers (db/list-email-subscribers (c/ensure-ds))
          :events (db/list-events (c/ensure-ds))
          :comments (db/list-all-comments (c/ensure-ds))
          :interactivity (db/interactivity (c/ensure-ds))}
         opts))

(defn dashboard-handler [req]
  (c/require-login req
    (fn [_]
      (c/html-response 200 (views/dashboard-page (page-data {}))))))

(defn update-settings-handler [req]
  (c/require-login req
    (fn [req]
      (let [raw (get-in req [:form-params "interactivity"])
            level (keyword (or raw ""))]
        (if (db/interactivity-levels level)
          (do
            (db/set-setting! (c/ensure-ds) "interactivity" (name level))
            (db/record-event! (c/ensure-ds)
              {:kind :settings
               :summary (str "Interactivity switched to " (name level))
               :actor "owner"})
            (c/redirect "/dashboard#settings"))
          (c/html-response 400
            (views/dashboard-page
              (page-data {:error (str "Unknown interactivity level: " (pr-str raw))}))))))))

(defn unsubscribe-handler
  "Remove someone from the list, with or without telling them. Silent is
  opt-in and explicit — an absent field still notifies, so a stale form
  cannot quietly suppress a mail."
  [req]
  (c/require-login req
    (fn [req]
      (let [email (str/trim (or (get-in req [:form-params "email"]) ""))
            reason (str/trim (or (get-in req [:form-params "reason"]) ""))
            silent? (= "silent" (get-in req [:form-params "notify"]))]
        (if (str/blank? email)
          (c/html-response 400
            (views/dashboard-page (page-data {:error "No email given."})))
          (do
            (db/unsubscribe-email! (c/ensure-ds) email)
            (db/record-event! (c/ensure-ds)
              {:kind :unsubscribe-by-owner
               :summary (str "Removed " email " from the subscriber list")
               :detail (when (not= reason "") (str "Reason given: " reason))
               :actor email
               :notified (if silent? :silent :mailed)})
            (when-not silent?
              (future
                (try
                  (mail/send-plain-email! email
                    "You have been unsubscribed"
                    (str "You have been removed from the eighttrigrams.net mailing list."
                         (when (not= reason "") (str "\n\nReason: " reason))))
                  (catch Exception e
                    (println "Failed to send unsubscribe email:" (.getMessage e))))))
            (c/redirect "/dashboard#subscribers")))))))
