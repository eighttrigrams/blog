(ns et.blog.server
  (:require [ring.adapter.jetty9 :as jetty]
            [et.blog.db :as db]
            [et.blog.auth :as auth]
            [et.blog.views :as views]
            [et.blog.render :as render]
            [et.blog.feed :as feed]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.middleware.params :refer [wrap-params]]
            [et.blog.middleware.rate-limit :refer [wrap-rate-limit]]
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
  (let [auth? (logged-in? req)
        articles (db/list-articles (ensure-ds) {:published-only? (not auth?)})]
    (html-response 200
      (views/home-page {:articles articles :logged-in? auth?}))))

(defn- article-handler [req]
  (let [auth? (logged-in? req)
        pub? (not auth?)
        id (Integer/parseInt (get-in req [:params :id]))
        as-of (get-in req [:params :as-of])
        ver (get-in req [:params :version])
        raw     (cond
                  as-of (db/get-article-version (ensure-ds) id as-of {})
                  ver   (let [v (Integer/parseInt ver)]
                          (if (and pub? (zero? v))
                            nil
                            (db/get-article-by-version (ensure-ds) id v {})))
                  :else (db/get-article (ensure-ds) id {:published-only? pub?}))
        article (if (and pub? raw (zero? (or (:version raw) 0)))
                  nil
                  raw)]
    (if article
      (let [versions (db/get-article-versions (ensure-ds) id {:published-only? pub?})
            fetch-fn (fn [aid as-of] (db/get-article-version (ensure-ds) aid as-of {}))
            rendered-content (render/render-content article fetch-fn)
            rendered-addenda (render/markdown->html (:addenda article))]
        (html-response 200
          (views/article-page {:article article :versions versions :logged-in? auth?
                               :current-version (:created_at article)
                               :rendered-content rendered-content
                               :rendered-addenda rendered-addenda})))
      (html-response 404
        (views/not-found-page {:logged-in? auth?})))))

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
            subtitle (or (get-in req [:form-params "subtitle"]) "")
            content (or (get-in req [:form-params "content"]) "")
            footnotes (or (get-in req [:form-params "footnotes"]) "")
            addenda (or (get-in req [:form-params "addenda"]) "")
            post-content (str/trim (or (get-in req [:form-params "post-content"]) ""))
            publish? (some? (get-in req [:form-params "publish"]))
            article {:title title :subtitle subtitle :content content :footnotes footnotes :addenda addenda}]
        (cond
          (str/blank? title)
          (html-response 400
            (views/edit-page {:new? true :logged-in? true :article article}))

          (and publish? (str/blank? post-content))
          (html-response 400
            (views/edit-page {:new? true :logged-in? true :article article
                              :error "Post content is required when publishing."
                              :post-content post-content}))

          :else
          (let [article-id (db/create-article! (ensure-ds) (merge article {:publish? publish?
                                                                            :post-content (when publish? post-content)}))]
            (redirect (str "/articles/" article-id))))))))

(defn- edit-article-handler [req]
  (require-login req
    (fn [req]
      (let [id (Integer/parseInt (get-in req [:params :id]))
            article (db/get-article (ensure-ds) id {})]
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
            subtitle (or (get-in req [:form-params "subtitle"]) "")
            content (or (get-in req [:form-params "content"]) "")
            footnotes (or (get-in req [:form-params "footnotes"]) "")
            addenda (or (get-in req [:form-params "addenda"]) "")
            post-content (str/trim (or (get-in req [:form-params "post-content"]) ""))
            publish? (some? (get-in req [:form-params "publish"]))
            article {:article_id id :title title :subtitle subtitle :content content :footnotes footnotes :addenda addenda}]
        (cond
          (str/blank? title)
          (html-response 400
            (views/edit-page {:article article :logged-in? true}))

          (and publish? (str/blank? post-content))
          (html-response 400
            (views/edit-page {:article article :logged-in? true
                              :error "Post content is required when publishing."
                              :post-content post-content}))

          :else
          (do
            (db/update-article! (ensure-ds) id (merge article {:publish? publish?
                                                                :post-content (when publish? post-content)}))
            (redirect (str "/articles/" id))))))))

(defn- confirm-delete-article-handler [req]
  (require-login req
    (fn [req]
      (let [id (Integer/parseInt (get-in req [:params :id]))
            article (db/get-article (ensure-ds) id {})]
        (if article
          (html-response 200
            (views/confirm-delete-article-page {:article article :logged-in? true}))
          (html-response 404
            (views/not-found-page {:logged-in? true})))))))

(defn- delete-article-handler [req]
  (require-login req
    (fn [_]
      (let [id (Integer/parseInt (get-in req [:params :id]))]
        (db/delete-article! (ensure-ds) id)
        (redirect "/")))))

(defn- deleted-articles-handler [req]
  (require-login req
    (fn [_]
      (let [articles (db/list-deleted-articles (ensure-ds))]
        (html-response 200
          (views/deleted-articles-page {:articles articles :logged-in? true}))))))

;; --- Posts ---

(defn- posts-handler [req]
  (let [auth? (logged-in? req)
        fetch-fn (fn [aid as-of] (db/get-article-version (ensure-ds) aid as-of {}))
        posts (db/list-posts (ensure-ds))
        post-ids (mapv :post_id posts)
        article-links (db/get-posts-article-links (ensure-ds) post-ids)
        posts (->> posts
                   (mapv #(assoc %
                            :rendered-content (render/render-content % fetch-fn)
                            :article-link (get article-links (:post_id %)))))]
    (html-response 200
      (views/posts-page {:posts posts :logged-in? auth?}))))

(defn- post-handler [req]
  (let [auth? (logged-in? req)
        id (Integer/parseInt (get-in req [:params :id]))
        as-of (get-in req [:params :as-of])
        post (if as-of
               (db/get-post-version (ensure-ds) id as-of {})
               (db/get-post (ensure-ds) id {}))]
    (if post
      (let [versions (if auth? (db/get-post-versions (ensure-ds) id {}) [post])
            fetch-fn (fn [aid as-of] (db/get-article-version (ensure-ds) aid as-of {}))
            rendered-content (render/render-content post fetch-fn)]
        (html-response 200
          (views/post-page {:post post :versions versions :logged-in? auth?
                            :current-version (:created_at post)
                            :rendered-content rendered-content})))
      (html-response 404
        (views/not-found-page {:logged-in? auth?})))))

(defn- new-post-handler [req]
  (require-login req
    (fn [req]
      (html-response 200
        (views/edit-post-page {:new? true :logged-in? (logged-in? req)})))))

(defn- create-post-handler [req]
  (require-login req
    (fn [_]
      (let [content (or (get-in req [:form-params "content"]) "")
            footnotes (or (get-in req [:form-params "footnotes"]) "")
            post-id (db/create-post! (ensure-ds) {:content content :footnotes footnotes})]
        (redirect (str "/posts/" post-id))))))

(defn- edit-post-handler [req]
  (require-login req
    (fn [req]
      (let [id (Integer/parseInt (get-in req [:params :id]))
            post (db/get-post (ensure-ds) id {})]
        (if post
          (html-response 200
            (views/edit-post-page {:post post :logged-in? true}))
          (html-response 404
            (views/not-found-page {:logged-in? true})))))))

(defn- update-post-handler [req]
  (require-login req
    (fn [_]
      (let [id (Integer/parseInt (get-in req [:params :id]))
            content (or (get-in req [:form-params "content"]) "")
            footnotes (or (get-in req [:form-params "footnotes"]) "")]
        (db/update-post! (ensure-ds) id {:content content :footnotes footnotes})
        (redirect (str "/posts/" id))))))

(defn- confirm-delete-post-handler [req]
  (require-login req
    (fn [req]
      (let [id (Integer/parseInt (get-in req [:params :id]))
            post (db/get-post (ensure-ds) id {})]
        (if post
          (html-response 200
            (views/confirm-delete-post-page {:post post :logged-in? true}))
          (html-response 404
            (views/not-found-page {:logged-in? true})))))))

(defn- delete-post-handler [req]
  (require-login req
    (fn [_]
      (let [id (Integer/parseInt (get-in req [:params :id]))]
        (db/delete-post! (ensure-ds) id)
        (redirect "/posts")))))

(defn- deleted-posts-handler [req]
  (require-login req
    (fn [_]
      (let [fetch-fn (fn [aid as-of] (db/get-article-version (ensure-ds) aid as-of {}))
            posts (db/list-deleted-posts (ensure-ds))
            post-ids (mapv :post_id posts)
            article-links (db/get-posts-article-links (ensure-ds) post-ids)
            posts (->> posts
                       (mapv #(assoc %
                                :rendered-content (render/render-content % fetch-fn)
                                :article-link (get article-links (:post_id %)))))]
        (html-response 200
          (views/deleted-posts-page {:posts posts :logged-in? true}))))))

(defn- site-url [req]
  (let [scheme (or (get-in req [:headers "x-forwarded-proto"]) "http")
        host (get-in req [:headers "host"] "localhost")]
    (str scheme "://" host)))

(defn- build-feed [req title feed-path posts]
  (let [fetch-fn (fn [aid as-of] (db/get-article-version (ensure-ds) aid as-of {}))
        post-ids (mapv :post_id posts)
        article-links (db/get-posts-article-links (ensure-ds) post-ids)
        rendered (mapv #(render/render-content % fetch-fn) posts)
        base (site-url req)]
    {:status 200
     :headers {"Content-Type" "application/atom+xml; charset=utf-8"}
     :body (feed/atom-feed {:title title
                            :feed-url (str base feed-path)
                            :site-url base
                            :posts posts
                            :article-links article-links
                            :rendered-posts rendered})}))

(defn- feed-all-handler [req]
  (let [posts (db/list-posts (ensure-ds))]
    (build-feed req "Blog - All Posts" "/feed.xml" posts)))

(defn- feed-articles-handler [req]
  (let [posts (db/list-article-posts (ensure-ds))]
    (build-feed req "Blog - Article Publications" "/feed/articles.xml" posts)))

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
  (GET "/articles/deleted" [] deleted-articles-handler)
  (GET "/articles/new" [] new-article-handler)
  (POST "/articles" [] create-article-handler)
  (GET ["/articles/:id/as-of/:as-of" :as-of #".*"] [] article-handler)
  (GET "/articles/:id/version/:version" [] article-handler)
  (GET "/articles/:id" [] article-handler)
  (GET "/articles/:id/edit" [] edit-article-handler)
  (GET "/articles/:id/delete" [] confirm-delete-article-handler)
  (POST "/articles/:id/delete" [] delete-article-handler)
  (POST "/articles/:id" [] update-article-handler)
  (GET "/posts" [] posts-handler)
  (GET "/posts/deleted" [] deleted-posts-handler)
  (GET "/posts/new" [] new-post-handler)
  (POST "/posts" [] create-post-handler)
  (GET ["/posts/:id/as-of/:as-of" :as-of #".*"] [] post-handler)
  (GET "/posts/:id" [] post-handler)
  (GET "/posts/:id/edit" [] edit-post-handler)
  (GET "/posts/:id/delete" [] confirm-delete-post-handler)
  (POST "/posts/:id/delete" [] delete-post-handler)
  (POST "/posts/:id" [] update-post-handler)
  (GET "/feed.xml" [] feed-all-handler)
  (GET "/feed/articles.xml" [] feed-articles-handler)
  (route/resources "/")
  (route/not-found (fn [_] (html-response 404 (views/not-found-page {:logged-in? false})))))

(defn- app [prod?]
  (-> app-routes
      wrap-params
      wrap-cookies
      (wrap-rate-limit (env-int "RATE_LIMIT_MAX_REQUESTS" (if prod? 180 720))
                       (env-int "RATE_LIMIT_WINDOW_SECONDS" 60))))

(defn- run-server [port]
  (let [host (or (System/getenv "HOST") "127.0.0.1")]
    (tel/log! :info (str "Binding to " host ":" port))
    (jetty/run-jetty (app (prod-mode?)) {:port port :host host :join? false})))

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
