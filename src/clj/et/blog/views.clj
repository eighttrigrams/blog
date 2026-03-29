(ns et.blog.views
  (:require [hiccup2.core :as h]
            [hiccup.util :as hu]))

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
      [:style
       "*, *::before, *::after { box-sizing: border-box; }
        body { font-family: Spectral, Georgia, serif; max-width: 728px; margin: 0 auto; padding: 1.5rem; line-height: 1.8; color: rgba(0,0,0,0.8); font-size: 1.125rem; }
        a { color: #FD5353; text-decoration: none; }
        a:hover { text-decoration: underline; }
        nav { border-bottom: 1px solid rgba(0,0,0,0.1); padding-bottom: 0.75rem; margin-bottom: 2.5rem; display: flex; justify-content: space-between; align-items: center; }
        nav a { color: rgba(0,0,0,0.8); margin-right: 1.25rem; }
        nav a:hover { color: #FD5353; }
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
        .post-preview { display: block; white-space: pre-wrap; }
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
        sup a { color: #FD5353; text-decoration: none; }
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
        .versions li { margin-bottom: 0.3rem; font-size: 0.95rem; color: rgba(0,0,0,0.6); }"]]
     [:body
      [:nav
       [:div
        [:a {:href "/"} "Blog"]
        [:a {:href "/posts"} "Posts"]
        (when logged-in?
          (list
            [:a {:href "/articles/new"} "New Article"]
            [:a {:href "/posts/new"} "New Post"]))]
       [:div
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

(defn article-page [{:keys [article versions logged-in? current-version rendered-content rendered-addenda]}]
  (let [{:keys [article_id title subtitle created_at version]} article]
    (layout {:title title :logged-in? logged-in?}
      [:article
       [:h1 (hu/escape-html title)]
       (when (and subtitle (not= subtitle ""))
         [:p.subtitle (hu/escape-html subtitle)])
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

(defn edit-page [{:keys [article logged-in? new?]}]
  (let [action (if new? "/articles" (str "/articles/" (:article_id article)))]
    (layout {:title (if new? "New Article" "Edit Article") :logged-in? logged-in?}
      [:form {:method "POST" :action action}
       [:div.edit-heading
        [:h1 (if new? "New Article" "Edit Article")]
        [:div.edit-actions
         [:button.btn {:type "submit"} "Save"]
         [:button.btn.btn-publish {:type "submit" :name "publish" :value "1"} "Publish"]]]
       [:div.form-group
        [:label {:for "title"} "Title"]
        [:input {:type "text" :name "title" :id "title" :value (or (:title article) "") :required true}]]
       [:div.form-group
        [:label {:for "subtitle"} "Subtitle"]
        [:input {:type "text" :name "subtitle" :id "subtitle" :value (or (:subtitle article) "")}]]
       [:div.form-group
        [:label {:for "content"} "Content"]
        [:textarea {:name "content" :id "content"} (hu/escape-html (or (:content article) ""))]]
       [:div.form-group
        [:label {:for "footnotes"} "Footnotes"]
        [:textarea {:name "footnotes" :id "footnotes"} (hu/escape-html (or (:footnotes article) ""))]]
       [:div.form-group
        [:label {:for "addenda"} "Addenda"]
        [:textarea {:name "addenda" :id "addenda"} (hu/escape-html (or (:addenda article) ""))]]])))

(defn posts-page [{:keys [posts logged-in?]}]
  (layout {:title "Posts" :logged-in? logged-in?}
    [:h1 "Posts"]
    (if (seq posts)
      [:ul.post-list
       (for [{:keys [post_id content created_at first_at]} posts]
         (let [preview (let [s (or content "")] (if (> (count s) 200) (str (subs s 0 200) "...") s))]
           [:li
            [:a {:href (str "/posts/" post_id)}
             [:span.post-preview (hu/escape-html preview)]]
            [:span.article-date (or first_at created_at)]]))]
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
         [:button.btn {:type "submit"} "Save"]]]
       [:div.form-group
        [:label {:for "content"} "Content"]
        [:textarea {:name "content" :id "content"} (hu/escape-html (or (:content post) ""))]]
       [:div.form-group
        [:label {:for "footnotes"} "Footnotes"]
        [:textarea {:name "footnotes" :id "footnotes"} (hu/escape-html (or (:footnotes post) ""))]]])))

(defn not-found-page [{:keys [logged-in?]}]
  (layout {:title "Not Found" :logged-in? logged-in?}
    [:h1 "Not Found"]
    [:p "The page you requested does not exist."]))
