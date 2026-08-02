(ns et.blog.handler.notes-users
  "Notes users — the only API credential blog has. A notes user may deliver a
  Note and do nothing else: its token authorises POST /api/notes, never an HTML
  page, and the rest of the API is public and ignores it."
  (:require [et.blog.handler.common :as c]
            [et.blog.auth :as auth]
            [et.blog.db :as db]
            [et.blog.views :as views]
            [buddy.hashers :as hashers]
            [clojure.string :as str])
  (:import [java.security SecureRandom]
           [java.util Base64]))

(defonce ^:private random (SecureRandom.))

(defn- generate-password []
  (let [bytes (byte-array 24)]
    (.nextBytes random bytes)
    (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) bytes)))

(defn login-api-handler
  "POST /api/auth/login — exchange a notes user's name and password for a bearer
  token, as {:token \"...\"}. The token authorises POST /api/notes and nothing
  else, and grants no access to the HTML pages. 401 on a wrong password, an
  unknown name or a revoked user, all alike."
  [req]
  (let [{:keys [username password]} (:body req)
        user (when (and (string? username) (string? password))
               (db/get-active-notes-user (c/ensure-ds) username))]
    (if (and user (hashers/check password (:password_hash user)))
      {:status 200 :body {:token (auth/create-notes-token (:name user))}}
      {:status 401 :body {:error "Invalid credentials"}})))

(defn- page-data [opts]
  (merge {:logged-in? true
          :notes-users (db/list-notes-users (c/ensure-ds))}
         opts))

(defn notes-users-page-handler [req]
  (c/require-login req
    (fn [_]
      (c/html-response 200 (views/notes-users-page (page-data {}))))))

(defn create-notes-user-handler [req]
  (c/require-login req
    (fn [req]
      (let [name (str/trim (or (get-in req [:form-params "name"]) ""))
            given (str/trim (or (get-in req [:form-params "password"]) ""))
            password (if (str/blank? given) (generate-password) given)]
        (if (str/blank? name)
          (c/html-response 400
            (views/notes-users-page (page-data {:error "Please enter a name."})))
          (try
            (db/create-notes-user! (c/ensure-ds) name (hashers/derive password))
            (c/html-response 200
              (views/notes-users-page
                (page-data {:created {:name name :password password}})))
            (catch Exception e
              (c/html-response 400
                (views/notes-users-page
                  (page-data {:error (if (re-find #"UNIQUE|constraint" (str (.getMessage e)))
                                       (str "There is already a notes user called " name ".")
                                       (str "Could not create the notes user: " (.getMessage e)))}))))))))))

(defn revoke-notes-user-handler [req]
  (c/require-login req
    (fn [req]
      (when-let [id (try (Integer/parseInt (get-in req [:params :id]))
                         (catch NumberFormatException _ nil))]
        (db/revoke-notes-user! (c/ensure-ds) id))
      (c/redirect "/notes-users"))))
