(ns et.blog.server
  (:require [ring.adapter.jetty9 :as jetty]
            [et.blog.db :as db]
            [et.blog.auth :as auth]
            [et.blog.views :as views]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.middleware.params :refer [wrap-params]]
            [nrepl.server :as nrepl]
            [taoensso.telemere :as tel])
  (:gen-class))

(defonce ds (atom nil))
(defonce *config (atom nil))

(defn- env-int [name default]
  (if-let [v (System/getenv name)]
    (try (Integer/parseInt v) (catch Exception _ default))
    default))

(defn- load-config []
  (let [config-file (io/file "config.edn")]
    (if (.exists config-file)
      (do
        (tel/log! :info "Loading configuration from config.edn")
        (edn/read-string (slurp config-file)))
      (do
        (tel/log! :info "config.edn not found, using defaults")
        {}))))

(defn- prod-mode? []
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

(defn- ensure-ds []
  (when (nil? @ds)
    (when (nil? @*config)
      (reset! *config (load-config)))
    (let [conn (db/init-conn (get @*config :db {:type :sqlite-memory}))]
      (reset! ds conn)))
  @ds)

(defn- admin-password []
  (or (System/getenv "ADMIN_PASSWORD") "admin"))

(defn- skip-logins? []
  (and (true? (:dangerously-skip-logins? @*config))
       (not (prod-mode?))))

(defn- logged-in? [req]
  (or (skip-logins?)
      (when-let [token (get-in req [:cookies "token" :value])]
        (some? (auth/verify-token token)))))

(defn- html-response [status body]
  {:status status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body body})

(defn- redirect [url]
  {:status 302 :headers {"Location" url}})

(defn- home-handler [req]
  (let [articles (db/list-articles (ensure-ds))]
    (html-response 200
      (views/home-page {:articles articles :logged-in? (logged-in? req)}))))

(defn- article-handler [req]
  (let [id (Integer/parseInt (get-in req [:params :id]))
        article (db/get-article (ensure-ds) id)]
    (if article
      (let [versions (when (logged-in? req) (db/get-article-versions (ensure-ds) id))]
        (html-response 200
          (views/article-page {:article article :versions versions :logged-in? (logged-in? req)})))
      (html-response 404
        (views/not-found-page {:logged-in? (logged-in? req)})))))

(defn- login-page-handler [req]
  (if (logged-in? req)
    (redirect "/")
    (html-response 200 (views/login-page {}))))

(defn- login-handler [req]
  (let [password (get-in req [:form-params "password"])]
    (if (= password (admin-password))
      (let [token (auth/create-token)]
        {:status 302
         :headers {"Location" "/"
                   "Set-Cookie" (str "token=" token "; Path=/; HttpOnly; SameSite=Strict")}})
      (html-response 401 (views/login-page {:error "Wrong password"})))))

(defn- logout-handler [_]
  {:status 302
   :headers {"Location" "/"
             "Set-Cookie" "token=; Path=/; HttpOnly; Max-Age=0"}})

(defn- require-login [req handler]
  (if (logged-in? req)
    (handler req)
    (redirect "/login")))

(defn- new-article-handler [req]
  (require-login req
    (fn [req]
      (html-response 200
        (views/edit-page {:new? true :logged-in? (logged-in? req)})))))

(defn- create-article-handler [req]
  (require-login req
    (fn [_]
      (let [title (str/trim (or (get-in req [:form-params "title"]) ""))
            content (or (get-in req [:form-params "content"]) "")]
        (if (str/blank? title)
          (html-response 400
            (views/edit-page {:new? true :logged-in? true
                              :article {:title title :content content}}))
          (let [article-id (db/create-article! (ensure-ds) title content)]
            (redirect (str "/articles/" article-id))))))))

(defn- edit-article-handler [req]
  (require-login req
    (fn [req]
      (let [id (Integer/parseInt (get-in req [:params :id]))
            article (db/get-article (ensure-ds) id)]
        (if article
          (html-response 200
            (views/edit-page {:article article :logged-in? true}))
          (html-response 404
            (views/not-found-page {:logged-in? true})))))))

(defn- update-article-handler [req]
  (require-login req
    (fn [_]
      (let [id (Integer/parseInt (get-in req [:params :id]))
            title (str/trim (or (get-in req [:form-params "title"]) ""))
            content (or (get-in req [:form-params "content"]) "")]
        (if (str/blank? title)
          (html-response 400
            (views/edit-page {:article {:article_id id :title title :content content} :logged-in? true}))
          (do
            (db/update-article! (ensure-ds) id title content)
            (redirect (str "/articles/" id))))))))

(defn- wrap-cookies [handler]
  (fn [req]
    (let [cookie-header (get-in req [:headers "cookie"] "")
          cookies (->> (str/split cookie-header #";\s*")
                       (filter #(str/includes? % "="))
                       (map #(let [[k v] (str/split % #"=" 2)] [k {:value v}]))
                       (into {}))]
      (handler (assoc req :cookies cookies)))))

(defroutes app-routes
  (GET "/" [] home-handler)
  (GET "/login" [] login-page-handler)
  (POST "/login" [] login-handler)
  (GET "/logout" [] logout-handler)
  (GET "/articles/new" [] new-article-handler)
  (POST "/articles" [] create-article-handler)
  (GET "/articles/:id" [] article-handler)
  (GET "/articles/:id/edit" [] edit-article-handler)
  (POST "/articles/:id" [] update-article-handler)
  (route/resources "/")
  (route/not-found (fn [_] (html-response 404 (views/not-found-page {:logged-in? false})))))

(defn- app []
  (-> app-routes
      wrap-params
      wrap-cookies))

(defn- run-server [port]
  (let [host (or (System/getenv "HOST") "127.0.0.1")]
    (tel/log! :info (str "Binding to " host ":" port))
    (jetty/run-jetty (app) {:port port :host host :join? false})))

(defn -main [& _args]
  (reset! *config (load-config))
  (let [prod? (prod-mode?)]
    (when (and (true? (:dangerously-skip-logins? @*config)) prod?)
      (throw (ex-info "Cannot use :dangerously-skip-logins? in production mode" {})))
    (tel/log! :info (str "Starting system in " (if prod? "production" "development") " mode"))
    (ensure-ds)
    (when-not prod?
      (when-let [nrepl-port (:nrepl-port @*config)]
        (nrepl/start-server :port nrepl-port)
        (spit ".nrepl-port" nrepl-port)
        (tel/log! :info (str "nREPL server started on port " nrepl-port))))
    (if-let [port (env-int "PORT" (:port @*config))]
      (do
        (tel/log! :info (str "Starting server on port " port))
        (run-server port)
        @(promise))
      (throw (ex-info "No port defined" {})))))
