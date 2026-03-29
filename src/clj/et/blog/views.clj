(ns et.blog.views
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [hiccup.util :as hu])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]))

(def ^:private human-date-fmt (DateTimeFormatter/ofPattern "MMMM d, yyyy"))

(defn- human-date [datetime-str]
  (when datetime-str
    (let [dt (LocalDateTime/parse datetime-str (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss"))]
      (.format dt human-date-fmt))))

(defn layout [{:keys [title logged-in?]} & body]
  (str
   (h/html
    [:html {:lang "en"}
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:title (if title (str title " - Blog") "Blog")]
      [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
      [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin ""}]
      [:link {:rel "stylesheet" :href "https://fonts.googleapis.com/css2?family=Spectral:ital,wght@0,400;0,600;1,400;1,600&display=swap"}]
      [:link {:rel "stylesheet" :href "/vendor/hljs/github.min.css"}]
      [:script {:src "/vendor/hljs/highlight.min.js"}]
      [:script {:src "/vendor/hljs/clojure.min.js"}]
      [:script "hljs.highlightAll();"]
      [:style
       "*, *::before, *::after { box-sizing: border-box; }
        body { font-family: Spectral, Georgia, serif; max-width: 728px; margin: 0 auto; padding: 1.5rem; line-height: 1.8; color: rgba(0,0,0,0.8); font-size: 1.125rem; }
        a { color: #1a0dab; text-decoration: none; }
        a:hover { text-decoration: underline; }
        a:visited { color: #1a0dab; }
        nav { border-bottom: 1px solid rgba(0,0,0,0.1); padding-bottom: 0.75rem; margin-bottom: 2.5rem; display: flex; justify-content: space-between; align-items: center; }
        nav a { color: rgba(0,0,0,0.8); margin-right: 1.25rem; }
        nav a:hover { color: #FD5353; }
        .nav-right { display: flex; align-items: center; gap: 0.75rem; }
        .feed-icon { color: rgba(0,0,0,0.4); display: flex; align-items: center; }
        .feed-icon:hover { color: #FD5353; text-decoration: none; }
        h1 { font-size: 2rem; font-weight: 600; line-height: 1.3; margin-bottom: 0.5rem; }
        h2 { font-size: 1.5rem; font-weight: 600; line-height: 1.3; }
        .subtitle { font-size: 1.2rem; color: rgba(0,0,0,0.5); margin-top: -0.5rem; margin-bottom: 0.5rem; }
        .article-list { list-style: none; padding: 0; }
        .article-list li { margin-bottom: 1.5rem; padding-bottom: 1.5rem; border-bottom: 1px solid rgba(0,0,0,0.08); }
        .article-list li:last-child { border-bottom: none; }
        .article-list a { color: rgba(0,0,0,0.8); }
        .article-list a:hover h2 { color: #FD5353; }
        .article-date { color: rgba(0,0,0,0.4); font-size: 0.9rem; }
        .post-list { list-style: none; padding: 0; }
        .post-list li { margin-bottom: 1.5rem; padding-bottom: 1.5rem; border-bottom: 1px solid rgba(0,0,0,0.08); }
        .post-list li:last-child { border-bottom: none; }
        .post-list a { color: rgba(0,0,0,0.8); }
        .post-list a:hover { color: #FD5353; }
        .post-heading { display: flex; justify-content: space-between; align-items: center; }
        .post-heading h2 { margin: 0; }
        .post-permalink { color: #1a0dab; text-decoration: none; }
        .post-permalink:hover { text-decoration: underline; }
        .post-article-link { margin-top: 0.75rem; font-weight: 600; }
        .article-content { margin-top: 1.5rem; }
        .article-content blockquote { border-left: 3px solid rgba(0,0,0,0.15); margin: 1rem 0; padding: 0.5rem 1rem; color: rgba(0,0,0,0.6); }
        .article-content code { background: rgba(0,0,0,0.05); padding: 0.15rem 0.4rem; border-radius: 3px; font-size: 0.9em; }
        .article-content pre code { display: block; padding: 1rem; overflow-x: auto; }
        .article-section { margin-top: 2rem; border-top: 1px solid rgba(0,0,0,0.08); padding-top: 1rem; }
        .article-section h3 { font-size: 1.1rem; font-weight: 600; color: rgba(0,0,0,0.6); margin-bottom: 0; }
        .footnotes { margin-top: 2rem; border-top: 1px solid rgba(0,0,0,0.08); padding-top: 1rem; }
        .footnotes h3 { font-size: 1rem; font-weight: 600; color: rgba(0,0,0,0.6); }
        .footnotes ol { padding-left: 1.5rem; font-size: 0.95rem; color: rgba(0,0,0,0.7); }
        .footnotes li { margin-bottom: 0.3rem; }
        sup a { color: #1a0dab; text-decoration: none; }
        sup.missing { color: #c00; font-size: 0.75rem; }
        .btn { display: inline-block; padding: 0.4rem 1rem; background: #FD5353; color: #fff; text-decoration: none; border: none; border-radius: 3px; cursor: pointer; font-size: 1rem; font-family: inherit; }
        .btn:hover { background: #e04848; text-decoration: none; }
        .btn-small { padding: 0.2rem 0.6rem; font-size: 0.85rem; }
        .btn-publish { background: #333; }
        .btn-publish:hover { background: #555; }
        .version-badge { display: inline-block; background: rgba(0,0,0,0.08); color: rgba(0,0,0,0.5); font-size: 0.8rem; padding: 0.1rem 0.5rem; border-radius: 3px; margin-left: 0.5rem; }
        .version-badge.draft { background: #fff3cd; color: #856404; }
        input[type=text], input[type=password], textarea { width: 100%; padding: 0.6rem; font-size: 1.125rem; font-family: inherit; border: 1px solid rgba(0,0,0,0.15); border-radius: 3px; }
        input[type=text]:focus, input[type=password]:focus, textarea:focus { outline: none; border-color: #FD5353; }
        textarea { min-height: 300px; resize: vertical; line-height: 1.8; }
        label { display: block; margin-bottom: 0.3rem; font-weight: 600; }
        .form-group { margin-bottom: 1.25rem; }
        .error { color: #FD5353; }
        .edit-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem; }
        .edit-heading h1 { margin: 0; }
        .edit-actions { display: flex; gap: 0.75rem; flex-shrink: 0; }
        .version-nav { display: flex; align-items: center; gap: 0.5rem; }
        .version-arrow { text-decoration: none; color: rgba(0,0,0,0.4); font-size: 1.1rem; padding: 0 0.2rem; }
        .version-arrow:hover { color: #FD5353; text-decoration: none; }
        .version-arrow.disabled { color: rgba(0,0,0,0.15); cursor: default; }
        .versions { margin-top: 2.5rem; border-top: 1px solid rgba(0,0,0,0.08); padding-top: 1.5rem; }
        .versions h3 { font-size: 1rem; font-weight: 600; color: rgba(0,0,0,0.6); }
        .versions ul { padding-left: 1.5rem; }
        .versions li { margin-bottom: 0.3rem; font-size: 0.95rem; color: rgba(0,0,0,0.6); }
        .btn-danger { background: #dc3545; }
        .btn-danger:hover { background: #c82333; }
        .btn-cancel { background: rgba(0,0,0,0.15); color: rgba(0,0,0,0.7); }
        .btn-cancel:hover { background: rgba(0,0,0,0.25); text-decoration: none; }
        .confirm-box { margin-top: 1.5rem; padding: 1.5rem; border: 1px solid rgba(0,0,0,0.1); border-radius: 5px; }
        .confirm-box .confirm-actions { display: flex; gap: 0.75rem; margin-top: 1rem; }"]]
     [:body
      [:nav
       [:div
        [:a {:href "/posts"} "Posts"]
        [:a {:href "/"} "Articles"]
        (when logged-in?
          (list
            [:a {:href "/posts/new"} "New Post"]
            [:a {:href "/articles/new"} "New Article"]))]
       [:div.nav-right
        [:a.feed-icon {:href "/feed/articles.xml" :title "Articles feed"}
         (h/raw "<svg width=\"14\" height=\"14\" viewBox=\"0 0 256 256\"><circle cx=\"68\" cy=\"189\" r=\"28\" fill=\"currentColor\"/><path d=\"M160 213h-34a89 89 0 0 0-89-89V90a123 123 0 0 1 123 123z\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"32\"/><path d=\"M220 213h-34a149 149 0 0 0-149-149V30a183 183 0 0 1 183 183z\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"32\"/></svg>")]
        [:a.feed-icon {:href "/feed.xml" :title "All posts feed"}
         (h/raw "<svg width=\"14\" height=\"14\" viewBox=\"0 0 256 256\"><circle cx=\"68\" cy=\"189\" r=\"28\" fill=\"currentColor\"/><path d=\"M160 213h-34a89 89 0 0 0-89-89V90a123 123 0 0 1 123 123z\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"32\"/><path d=\"M220 213h-34a149 149 0 0 0-149-149V30a183 183 0 0 1 183 183z\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"32\"/></svg>")]
        (if logged-in?
          [:a {:href "/logout"} "Logout"]
          [:a {:href "/login"} "Login"])]]
      body]])))

(defn home-page [{:keys [articles logged-in?]}]
  (layout {:title nil :logged-in? logged-in?}
    (if (seq articles)
      [:ul.article-list
       (for [{:keys [article_id title created_at]} articles]
         [:li
          [:a {:href (str "/articles/" article_id)}
           [:h2 (hu/escape-html title)]]
          [:span.article-date created_at]])]
      [:p "No articles yet."])))

(defn- version-nav [base-path id-key entity created_at versions]
  (let [sorted (sort-by :created_at versions)
        idx (.indexOf (mapv :created_at sorted) created_at)
        prev-v (when (pos? idx) (nth sorted (dec idx)))
        next-v (when (< idx (dec (count sorted))) (nth sorted (inc idx)))
        eid (get entity id-key)]
    [:div.version-nav
     (if prev-v
       [:a.version-arrow {:href (str base-path eid "/as-of/" (:created_at prev-v))} "\u2190"]
       [:span.version-arrow.disabled "\u2190"])
     [:span.article-date created_at]
     (if next-v
       [:a.version-arrow {:href (str base-path eid "/as-of/" (:created_at next-v))} "\u2192"]
       [:span.version-arrow.disabled "\u2192"])]))

(defn- word-count [text]
  (if (str/blank? text) 0
    (count (str/split (str/trim text) #"\s+"))))

(defn article-page [{:keys [article versions logged-in? current-version rendered-content rendered-addenda]}]
  (let [{:keys [article_id title subtitle created_at version content]} article]
    (layout {:title title :logged-in? logged-in?}
      [:article
       [:h1 (hu/escape-html title)]
       (when (and subtitle (not= subtitle ""))
         [:p.subtitle (hu/escape-html subtitle)])
       [:p.word-count (str (word-count content) " words")]
       (if (> (count versions) 1)
         (version-nav "/articles/" :article_id article (or current-version created_at) versions)
         [:div.version-nav [:span.article-date created_at]])
       (if (and version (pos? version))
         [:span.version-badge (str "v" version)]
         (when logged-in? [:span.version-badge.draft "draft"]))
       (when logged-in?
         [:span " " [:a.btn.btn-small {:href (str "/articles/" article_id "/edit")} "Edit"]])
       [:div.article-content (h/raw rendered-content)]
       (when rendered-addenda
         [:div.article-section
          [:h3 "Addenda"]
          [:div.article-content (h/raw rendered-addenda)]])])))

(defn login-page [{:keys [error]}]
  (layout {:title "Login"}
    [:h1 "Login"]
    (when error
      [:p.error error])
    [:form {:method "POST" :action "/login"}
     [:div.form-group
      [:label {:for "password"} "Password"]
      [:input {:type "password" :name "password" :id "password" :autofocus true}]]
     [:button.btn {:type "submit"} "Login"]]))

(defn edit-page [{:keys [article logged-in? new? error post-content]}]
  (let [action (if new? "/articles" (str "/articles/" (:article_id article)))]
    (layout {:title (if new? "New Article" "Edit Article") :logged-in? logged-in?}
      (when error
        [:p.error error])
      [:form {:method "POST" :action action}
       [:div.edit-heading
        [:h1 (if new? "New Article" "Edit Article")]
        [:div.edit-actions
         [:button.btn {:type "submit"} "Save"]
         [:button.btn.btn-publish {:type "submit" :name "publish" :value "1"} "Publish"]
         (when-not new?
           [:a.btn.btn-small.btn-danger {:href (str "/articles/" (:article_id article) "/delete")} "Delete"])]]
       [:div.form-group
        [:label {:for "title"} "Title"]
        [:input {:type "text" :name "title" :id "title" :value (or (:title article) "") :required true}]]
       [:div.form-group
        [:label {:for "subtitle"} "Subtitle"]
        [:input {:type "text" :name "subtitle" :id "subtitle" :value (or (:subtitle article) "")}]]
       [:div.form-group
        [:label {:for "content"} "Content"]
        [:textarea {:name "content" :id "content"} (or (:content article) "")]]
       [:div.form-group
        [:label {:for "footnotes"} "Footnotes"]
        [:textarea {:name "footnotes" :id "footnotes"} (or (:footnotes article) "")]]
       [:div.form-group
        [:label {:for "addenda"} "Addenda"]
        [:textarea {:name "addenda" :id "addenda"} (or (:addenda article) "")]]
       [:details (when error {:open true})
        [:summary "Post content (required for publishing) - consider an article abstract"]
        [:div.form-group
         [:textarea {:name "post-content" :id "post-content"} (or post-content "")]]]])))

(defn posts-page [{:keys [posts logged-in?]}]
  (layout {:title "Posts" :logged-in? logged-in?}
    [:h1 "Posts"]
    (if (seq posts)
      [:ul.post-list
       (for [{:keys [post_id created_at first_at rendered-content article-link]} posts]
         [:li
          [:div.post-heading
           [:h2 [:a.post-permalink {:href (str "/posts/" post_id)} "#"] " " (human-date (or first_at created_at))]
           (when logged-in?
             [:a.btn.btn-small {:href (str "/posts/" post_id "/edit")} "Edit"])]
          [:div.article-content (h/raw rendered-content)]
          (when article-link
            [:p.post-article-link
             [:a {:href (str "/articles/" (:article_id article-link) "/version/" (:article_version article-link))}
              (hu/escape-html (:title article-link))
              " \u2192"]])])]
      [:p "No posts yet."])))

(defn post-page [{:keys [post versions logged-in? current-version rendered-content]}]
  (let [{:keys [post_id created_at]} post]
    (layout {:title "Post" :logged-in? logged-in?}
      [:article
       (if (and logged-in? (> (count versions) 1))
         (version-nav "/posts/" :post_id post (or current-version created_at) versions)
         [:div.version-nav [:span.article-date created_at]])
       (when logged-in?
         [:span [:a.btn.btn-small {:href (str "/posts/" post_id "/edit")} "Edit"]])
       [:div.article-content (h/raw rendered-content)]])))

(defn edit-post-page [{:keys [post logged-in? new?]}]
  (let [action (if new? "/posts" (str "/posts/" (:post_id post)))]
    (layout {:title (if new? "New Post" "Edit Post") :logged-in? logged-in?}
      [:form {:method "POST" :action action}
       [:div.edit-heading
        [:h1 (if new? "New Post" "Edit Post")]
        [:div.edit-actions
         [:button.btn {:type "submit"} "Save"]
         (when-not new?
           [:a.btn.btn-small.btn-danger {:href (str "/posts/" (:post_id post) "/delete")} "Delete"])]]
       [:div.form-group
        [:label {:for "content"} "Content"]
        [:textarea {:name "content" :id "content"} (or (:content post) "")]]
      ])))

(defn confirm-delete-article-page [{:keys [article logged-in?]}]
  (layout {:title "Delete Article" :logged-in? logged-in?}
    [:h1 "Delete Article"]
    [:div.confirm-box
     [:p "Are you sure you want to delete \"" (hu/escape-html (:title article)) "\"?"]
     [:div.confirm-actions
      [:form {:method "POST" :action (str "/articles/" (:article_id article) "/delete")}
       [:button.btn.btn-danger {:type "submit"} "Delete"]]
      [:a.btn.btn-cancel {:href (str "/articles/" (:article_id article) "/edit")} "Cancel"]]]))

(defn confirm-delete-post-page [{:keys [post logged-in?]}]
  (layout {:title "Delete Post" :logged-in? logged-in?}
    [:h1 "Delete Post"]
    [:div.confirm-box
     [:p "Are you sure you want to delete the post from " (human-date (:created_at post)) "?"]
     [:div.confirm-actions
      [:form {:method "POST" :action (str "/posts/" (:post_id post) "/delete")}
       [:button.btn.btn-danger {:type "submit"} "Delete"]]
      [:a.btn.btn-cancel {:href (str "/posts/" (:post_id post) "/edit")} "Cancel"]]]))

(defn deleted-articles-page [{:keys [articles logged-in?]}]
  (layout {:title "Deleted Articles" :logged-in? logged-in?}
    [:h1 "Deleted Articles"]
    (if (seq articles)
      [:ul.article-list
       (for [{:keys [article_id title created_at]} articles]
         [:li
          [:h2 (hu/escape-html title)]
          [:span.article-date created_at]])]
      [:p "No deleted articles."])))

(defn deleted-posts-page [{:keys [posts logged-in?]}]
  (layout {:title "Deleted Posts" :logged-in? logged-in?}
    [:h1 "Deleted Posts"]
    (if (seq posts)
      [:ul.post-list
       (for [{:keys [created_at first_at rendered-content]} posts]
         [:li
          [:div.post-heading
           [:h2 (human-date (or first_at created_at))]]
          [:div.article-content (h/raw rendered-content)]])]
      [:p "No deleted posts."])))

(defn not-found-page [{:keys [logged-in?]}]
  (layout {:title "Not Found" :logged-in? logged-in?}
    [:h1 "Not Found"]
    [:p "The page you requested does not exist."]))
