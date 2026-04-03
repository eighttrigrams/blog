(ns et.blog.handler.auth
  (:require [et.blog.handler.common :as c]
            [et.blog.auth :as auth]
            [et.blog.views :as views]))

(defn login-page-handler [req]
  (if (c/logged-in? req)
    (c/redirect "/")
    (c/html-response 200 (views/login-page {}))))

(defn login-handler [req]
  (let [password (get-in req [:form-params "password"])]
    (if (= password (c/admin-password))
      (let [token (auth/create-token)]
        {:status 302
         :headers {"Location" "/"
                   "Set-Cookie" (str "token=" token "; Path=/; HttpOnly; SameSite=Strict")}})
      (c/html-response 401 (views/login-page {:error "Wrong password"})))))

(defn logout-handler [_]
  {:status 302
   :headers {"Location" "/"
             "Set-Cookie" "token=; Path=/; HttpOnly; Max-Age=0"}})
