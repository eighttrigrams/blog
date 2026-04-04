(ns et.blog.auth
  (:require [buddy.sign.jwt :as jwt]))

(defn jwt-secret []
  (or (System/getenv "ADMIN_PASSWORD")
      (when (= "true" (System/getenv "DEV")) "dev-secret")
      (throw (ex-info "ADMIN_PASSWORD env var is required" {}))))

(defn create-token []
  (jwt/sign {:admin true} (jwt-secret)))

(defn verify-token [token]
  (try
    (jwt/unsign token (jwt-secret))
    (catch Exception _ nil)))
