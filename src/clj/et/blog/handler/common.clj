(ns et.blog.handler.common
  (:require [et.blog.auth :as auth]
            [et.blog.db :as db]
            [clojure.java.io :as io]
            [aero.core :as aero]
            [taoensso.telemere :as tel]))

(defonce ds (atom nil))
(defonce *config (atom nil))

(defn env-int [name default]
  (if-let [v (System/getenv name)]
    (try (Integer/parseInt v) (catch Exception _ default))
    default))

(defn load-config []
  (let [config-file (io/file "config.edn")]
    (if (.exists config-file)
      (do
        (tel/log! :info "Loading configuration from config.edn")
        (aero/read-config config-file))
      (do
        (tel/log! :info "config.edn not found, using defaults")
        {}))))

(defn prod-mode? []
  (let [on-fly? (some? (System/getenv "FLY_APP_NAME"))
        dev-mode? (= "true" (System/getenv "DEV"))
        admin-pw (System/getenv "ADMIN_PASSWORD")]
    (cond
      (or on-fly? (not dev-mode?))
      (do (when-not admin-pw
            (throw (ex-info "ADMIN_PASSWORD required in production" {})))
          true)
      admin-pw true
      :else false)))

(defn ensure-ds []
  (when (nil? @ds)
    (when (nil? @*config)
      (reset! *config (load-config)))
    (let [conn (db/init-conn (get @*config :db {:type :sqlite-memory}))]
      (reset! ds conn)))
  @ds)

(defn admin-password []
  (if-let [pw (System/getenv "ADMIN_PASSWORD")]
    pw
    (if (System/getenv "FLY_APP_NAME")
      (throw (ex-info "ADMIN_PASSWORD env var is required" {}))
      "admin")))

(defn- skip-logins? []
  (and (true? (:dangerously-skip-logins? @*config))
       (not (prod-mode?))))

(defn logged-in? [req]
  (or (skip-logins?)
      (boolean
        (when-let [token (get-in req [:cookies "token" :value])]
          ;; Notes tokens are signed with the same secret, so the :admin claim —
          ;; not merely a valid signature — is what makes a reader the owner.
          (true? (:admin (auth/verify-token token)))))))

(defn- bearer-token [req]
  (when-let [header (get-in req [:headers "authorization"])]
    (second (re-find #"(?i)^Bearer\s+(\S+)$" header))))

(defn notes-user
  "The name of the notes user this request authenticates as, or nil. A token is
  only good while its row is present and unrevoked, so revoking takes effect on
  the next request rather than when some cached token expires."
  [req]
  (when-let [name (some-> (bearer-token req) auth/verify-token :notes-user)]
    (when (db/get-active-notes-user (ensure-ds) name)
      name)))

(defn html-response [status body]
  {:status status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body body})

(defn text-response [status body]
  {:status status
   :headers {"Content-Type" "text/plain; charset=utf-8"}
   :body body})

(defn redirect [url]
  {:status 302 :headers {"Location" url}})

(defn resolve-image-field [m k]
  (if-let [img (not-empty (get m k))]
    (let [base (or (:image-base-url @*config) "")]
      (assoc m k (str base "/" img)))
    m))

(defn resolve-preview-image [article]
  (resolve-image-field article :preview_image))

(defn site-url [req]
  (let [scheme (or (get-in req [:headers "x-forwarded-proto"]) "http")
        host (get-in req [:headers "host"] "localhost")]
    (str scheme "://" host)))

(defn require-login [req handler]
  (if (logged-in? req)
    (handler req)
    (redirect "/login")))
