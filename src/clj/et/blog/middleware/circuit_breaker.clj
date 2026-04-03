(ns et.blog.middleware.circuit-breaker
  (:require [et.blog.tracker :as tracker]))

(def ^:private request-log (atom []))
(def ^:private tripped (atom false))
(def ^:private notified (atom false))

(defn- current-time-ms []
  (System/currentTimeMillis))

(def ^:private window-ms (* 60 60 1000))
(def ^:private max-requests 100)

(defn- prune-and-count []
  (let [cutoff (- (current-time-ms) window-ms)
        pruned (filterv #(> % cutoff) @request-log)]
    (reset! request-log pruned)
    (count pruned)))

(defn- trip! []
  (reset! tripped true)
  (when (compare-and-set! notified false true)
    (future
      (try
        (tracker/send-urgent-message!
          "Circuit breaker tripped"
          "The subscribe/message endpoints have been shut down due to excessive requests (>100/hour)."
          "eighttrigrams.net")
        (catch Exception e
          (println "Failed to notify tracker:" (.getMessage e)))))))

(defn check-and-record!
  "Records a request and returns true if allowed, false if tripped."
  []
  (if @tripped
    false
    (do
      (swap! request-log conj (current-time-ms))
      (when (> (prune-and-count) max-requests)
        (trip!))
      (not @tripped))))
