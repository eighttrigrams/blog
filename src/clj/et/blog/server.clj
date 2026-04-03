(ns et.blog.server
  (:require [ring.adapter.jetty9 :as jetty]
            [clojure.string :as str]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.middleware.params :refer [wrap-params]]
            [et.blog.middleware.rate-limit :refer [wrap-rate-limit]]
            [et.blog.render :as render]
            [et.blog.views :as views]
            [et.blog.handler.common :as c]
            [et.blog.handler.auth :as auth-h]
            [et.blog.handler.articles :as articles-h]
            [et.blog.handler.comments :as comments-h]
            [et.blog.handler.posts :as posts-h]
            [et.blog.handler.email :as email-h]
            [et.blog.handler.feed :as feed-h]
            [nrepl.server :as nrepl]
            [taoensso.telemere :as tel])
  (:gen-class))

(def ds c/ds)
(def *config c/*config)

(defn- wrap-cookies [handler]
  (fn [req]
    (let [cookie-header (get-in req [:headers "cookie"] "")
          cookies (->> (str/split cookie-header #";\s*")
                       (filter #(str/includes? % "="))
                       (map #(let [[k v] (str/split % #"=" 2)] [k {:value v}]))
                       (into {}))]
      (handler (assoc req :cookies cookies)))))

(defroutes app-routes
  (GET "/" [] articles-h/home-handler)
  (GET "/articles" [] articles-h/home-handler)
  (GET "/login" [] auth-h/login-page-handler)
  (POST "/login" [] auth-h/login-handler)
  (GET "/logout" [] auth-h/logout-handler)
  (GET "/replies/:id/delete" [] comments-h/confirm-delete-reply-handler)
  (POST "/replies/:id/delete" [] comments-h/delete-reply-handler)
  (GET "/comments/:id/reply" [] comments-h/reply-form-handler)
  (POST "/comments/:id/reply" [] comments-h/reply-submit-handler)
  (GET "/comments/:id/delete" [] comments-h/confirm-delete-comment-handler)
  (POST "/comments/:id/delete" [] comments-h/delete-comment-handler)
  (GET "/article/deleted" [] articles-h/deleted-articles-handler)
  (GET "/article/drafts" [] articles-h/drafts-handler)
  (GET "/article/new" [] articles-h/new-article-handler)
  (POST "/article" [] articles-h/create-article-handler)
  (GET ["/article/:id/as-of/:as-of" :as-of #".*"] [] articles-h/article-handler)
  (GET "/article/:id/version/:version/comment/:comment-id" [] comments-h/comment-page-handler)
  (GET "/article/:id/version/:version/comments" [] comments-h/version-comments-handler)
  (GET "/article/:id/version/:version/comment" [] comments-h/comment-form-handler)
  (POST "/article/:id/version/:version/comment" [] comments-h/comment-submit-handler)
  (GET "/article/:id/version/:version" [] articles-h/article-handler)
  (GET "/article/:id/comments" [] comments-h/article-comments-handler)
  (GET "/article/:id" [] articles-h/article-handler)
  (GET "/article/:id/edit" [] articles-h/edit-article-handler)
  (GET "/article/:id/delete" [] articles-h/confirm-delete-article-handler)
  (POST "/article/:id/delete" [] articles-h/delete-article-handler)
  (POST "/article/:id" [] articles-h/update-article-handler)
  (GET "/posts" [] posts-h/posts-handler)
  (GET "/post/deleted" [] posts-h/deleted-posts-handler)
  (GET "/post/new" [] posts-h/new-post-handler)
  (POST "/posts" [] posts-h/create-post-handler)
  (GET ["/post/:id/as-of/:as-of" :as-of #".*"] [] posts-h/post-handler)
  (GET "/post/:id" [] posts-h/post-handler)
  (GET "/post/:id/edit" [] posts-h/edit-post-handler)
  (GET "/post/:id/delete" [] posts-h/confirm-delete-post-handler)
  (POST "/post/:id/delete" [] posts-h/delete-post-handler)
  (POST "/post/:id" [] posts-h/update-post-handler)
  (GET "/email" [] email-h/email-page-handler)
  (POST "/email" [] email-h/email-submit-handler)
  (POST "/email/message" [] email-h/message-submit-handler)
  (GET "/feed/posts.xml" [] feed-h/feed-posts-handler)
  (GET "/feed/articles.xml" [] feed-h/feed-articles-handler)
  (route/resources "/")
  (route/not-found (fn [_] (c/html-response 404 (views/not-found-page {:logged-in? false})))))

(defn- app [prod?]
  (-> app-routes
      wrap-params
      wrap-cookies
      (wrap-rate-limit (c/env-int "RATE_LIMIT_MAX_REQUESTS" (if prod? 180 720))
                       (c/env-int "RATE_LIMIT_WINDOW_SECONDS" 60))))

(defn- run-server [port]
  (let [host (or (System/getenv "HOST") "127.0.0.1")]
    (tel/log! :info (str "Binding to " host ":" port))
    (jetty/run-jetty (app (c/prod-mode?)) {:port port :host host :join? false})))

(defn -main [& _args]
  (reset! c/*config (c/load-config))
  (let [prod? (c/prod-mode?)]
    (when (and (true? (:dangerously-skip-logins? @c/*config)) prod?)
      (throw (ex-info "Cannot use :dangerously-skip-logins? in production mode" {})))
    (tel/log! :info (str "Starting system in " (if prod? "production" "development") " mode"))
    (when-let [base-url (:image-base-url @c/*config)]
      (render/set-image-base-url! base-url))
    (c/ensure-ds)
    (when-not prod?
      (when-let [nrepl-port (:nrepl-port @c/*config)]
        (nrepl/start-server :port nrepl-port)
        (spit ".nrepl-port" nrepl-port)
        (tel/log! :info (str "nREPL server started on port " nrepl-port))))
    (if-let [port (c/env-int "PORT" (:port @c/*config))]
      (do
        (tel/log! :info (str "Starting server on port " port))
        (run-server port)
        @(promise))
      (tel/log! :info "No PORT configured, starting without web server"))))
