(ns et.blog.handler.api
  "The read-only JSON API. Every handler here answers as an anonymous visitor and
  reads through `et.blog.handler.visibility`, so the API can show a published
  article, its published versions and its comment threads, and never a draft, a
  deleted article or anything else the HTML keeps behind a login. Content comes
  back as the data model stores it — the same source the HTML renders from.

  The docstrings are the API documentation: GET /api/describe hands them out, and
  each one opens with the `METHOD /path` it answers."
  ;; The notes namespaces are required for their side effect on `find-ns` below:
  ;; describe walks them, so they must be loaded whenever describe can be called.
  (:require [et.blog.handler.common :as c]
            [et.blog.handler.visibility :as vis]
            [et.blog.handler.notes-users]
            [et.blog.db :as db]))

;; The API has no auth surface at all, so this is the only reader it ever serves
;; and there is no request from which `pub?` could come out false.
(def ^:private as-visitor {:pub? true})

(def ^:private not-found {:status 404 :body {:error "Not found"}})

(defn- id-param
  "A numeric path segment, or nil when it is not a number. An id that cannot be
  parsed names nothing, which is a 404 like any other unknown id — not a 500."
  [req k]
  (when-let [v (get-in req [:params k])]
    (try (Integer/parseInt v)
         (catch NumberFormatException _ nil))))

(def ^:private article-keys
  [:article_id :title :subtitle :version :created_at
   :content :footnotes :addenda :preamble
   :preview_image :abstract :topics])

(def ^:private summary-keys
  [:article_id :title :subtitle :version :created_at
   :preview_image :abstract :topics])

(defn- article-body [article]
  (-> (select-keys article article-keys)
      c/resolve-preview-image))

(defn- article-summary
  "A listing entry. `listed-articles` has resolved the preview image already, so
  this only renames the announcement keys into the snake_case the rest of the
  responses use."
  [article]
  (assoc (select-keys article summary-keys)
         :latest_version (:latest-version article)
         :latest_published_at (:latest-published-at article)))

(defn- version-summary [row]
  (select-keys row [:article_id :version :created_at :title :subtitle]))

;; Comments and replies are selected key by key rather than passed through: the
;; commenter's email address sits in the same table and must never reach a
;; response.
(defn- reply-body [reply]
  (select-keys reply [:id :comment_id :display_name :body :created_at]))

(defn- comment-body [comment]
  (assoc (select-keys comment [:id :article_id :article_version :display_name :body :created_at])
         :replies (mapv reply-body (:replies comment))))

(defn- addressable-versions
  "One entry per version the version endpoints can serve. An edit inside a version
  adds a row and keeps the number, and those endpoints answer with the newest row
  of the version, so the listing collapses to that same row."
  [rows]
  (->> rows
       (group-by :version)
       (map (fn [[_ rows]] (first (sort-by :created_at #(compare %2 %1) rows))))
       (sort-by :version #(compare %2 %1))
       (mapv version-summary)))

(def ^:private describe-namespaces
  "Namespaces whose public vars back API routes. GET /api/describe walks these to
  enumerate the surface from var metadata, so a handler's docstring *is* its
  documentation."
  '[et.blog.handler.api
    et.blog.handler.notes-users])

(def ^:private route-doc-re
  "Route handlers document themselves as `METHOD /path — explanation`. Matching on
  that keeps helpers out of the description, so it only advertises what can
  actually be called."
  #"(?s)^(GET|POST|PUT|DELETE|PATCH)\s+(\S+)\s")

(defn describe-handler
  "GET /api/describe — enumerate the API surface: every route handler with its
  method, path and docstring. Read-only and public, so a client can discover the
  endpoints before calling them."
  [_req]
  {:status 200
   :body (->> describe-namespaces
              (mapcat (fn [ns-sym] (when-let [n (find-ns ns-sym)] (ns-publics n))))
              (keep (fn [[sym v]]
                      (let [doc (:doc (meta v))]
                        (when-let [[_ method path] (some->> doc (re-find route-doc-re))]
                          {:name (str sym)
                           :ns (str (ns-name (.ns ^clojure.lang.Var v)))
                           :method method
                           :path path
                           :arglists (pr-str (:arglists (meta v)))
                           :doc doc}))))
              (sort-by (juxt :path :method))
              vec)})

(defn list-articles-handler
  "GET /api/articles — the published articles, one entry each at its newest
  version, latest announcement first. The landing article is the front page
  rather than a list entry, so it is left out here exactly as it is on the public
  home page; it is still readable at its own path."
  [_req]
  {:status 200
   :body (mapv article-summary (vis/listed-articles (c/ensure-ds) as-visitor))})

(defn get-article-handler
  "GET /api/articles/:id — one published article at its current version: its
  metadata and its stored content, from the same read the article page uses. 404
  for a draft, a deleted article or an unknown id, all alike."
  [req]
  (let [id (id-param req :id)
        article (when id (vis/visible-article (c/ensure-ds) id as-visitor))]
    (if article
      {:status 200 :body (article-body article)}
      not-found)))

(defn list-article-versions-handler
  "GET /api/articles/:id/versions — the article's published versions, newest
  first, one entry per version that can be asked for by number. 404 when the
  article itself is not public."
  [req]
  (let [ds (c/ensure-ds)
        id (id-param req :id)
        article (when id (vis/visible-article ds id as-visitor))]
    (if article
      {:status 200 :body (addressable-versions (vis/visible-versions ds id as-visitor))}
      not-found)))

(defn get-article-version-handler
  "GET /api/articles/:id/versions/:version — one published version of an article,
  the metadata and content its version page shows. 404 for version 0, which is
  the owner's working copy, and for a version that does not exist."
  [req]
  (let [id (id-param req :id)
        version (id-param req :version)
        article (when (and id version)
                  (vis/visible-article (c/ensure-ds) id (assoc as-visitor :version version)))]
    (if article
      {:status 200 :body (article-body article)}
      not-found)))

(defn list-article-comments-handler
  "GET /api/articles/:id/comments — every comment on the article whatever version
  it was left on, newest first, each with its replies nested. The thread the
  article's comments page shows a visitor. 404 when the article is not public."
  [req]
  (let [ds (c/ensure-ds)
        id (id-param req :id)
        article (when id (vis/visible-article ds id as-visitor))]
    (if article
      {:status 200
       :body (mapv comment-body (vis/with-replies ds (db/get-comments-for-article ds id)))}
      not-found)))

(defn list-version-comments-handler
  "GET /api/articles/:id/versions/:version/comments — the comments left on that
  one version, newest first, each with its replies nested. 404 when the version
  is not public; version 0 never has a thread."
  [req]
  (let [ds (c/ensure-ds)
        id (id-param req :id)
        version (id-param req :version)
        article (when (and id version (pos? version))
                  (vis/visible-article ds id (assoc as-visitor :version version)))]
    (if article
      {:status 200
       :body (mapv comment-body (vis/with-replies ds (db/get-comments-for-version ds id version)))}
      not-found)))
