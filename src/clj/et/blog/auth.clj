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

(defn create-notes-token
  "A bearer token for a notes user. It names the user and carries no :admin
  claim, so it can never stand in for the owner's login."
  [name]
  (jwt/sign {:notes-user name} (jwt-secret)))

(defn verify-token [token]
  (try
    (jwt/unsign token (jwt-secret))
    (catch Exception _ nil)))
