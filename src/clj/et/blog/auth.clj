(ns et.blog.auth
  (:require [buddy.sign.jwt :as jwt]))

(defn jwt-secret []
  (if-let [pw (System/getenv "ADMIN_PASSWORD")]
    pw
    (if (System/getenv "FLY_APP_NAME")
      (throw (ex-info "ADMIN_PASSWORD env var is required" {}))
      "dev-secret")))

(defn create-token []
  (jwt/sign {:admin true} (jwt-secret)))

(defn verify-token [token]
  (try
    (jwt/unsign token (jwt-secret))
    (catch Exception _ nil)))
