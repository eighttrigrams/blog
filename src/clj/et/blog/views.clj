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
      [:style
       "*, *::before, *::after { box-sizing: border-box; }
        body { font-family: Georgia, serif; max-width: 720px; margin: 0 auto; padding: 1rem; line-height: 1.6; color: #333; }
        nav { border-bottom: 1px solid #ccc; padding-bottom: 0.5rem; margin-bottom: 2rem; display: flex; justify-content: space-between; align-items: center; }
        nav a { text-decoration: none; color: #333; margin-right: 1rem; }
        nav a:hover { text-decoration: underline; }
        h1 { font-size: 1.8rem; }
        h2 { font-size: 1.4rem; }
        .article-list { list-style: none; padding: 0; }
        .article-list li { margin-bottom: 1.5rem; padding-bottom: 1.5rem; border-bottom: 1px solid #eee; }
        .article-list li:last-child { border-bottom: none; }
        .article-list a { text-decoration: none; color: #333; }
        .article-list a:hover { text-decoration: underline; }
        .article-date { color: #999; font-size: 0.9rem; }
        .article-content { white-space: pre-wrap; }
        .btn { display: inline-block; padding: 0.4rem 1rem; background: #333; color: #fff; text-decoration: none; border: none; cursor: pointer; font-size: 1rem; font-family: inherit; }
        .btn:hover { background: #555; }
        .btn-small { padding: 0.2rem 0.6rem; font-size: 0.9rem; }
        input[type=text], input[type=password], textarea { width: 100%; padding: 0.5rem; font-size: 1rem; font-family: inherit; border: 1px solid #ccc; }
        textarea { min-height: 300px; resize: vertical; }
        label { display: block; margin-bottom: 0.3rem; font-weight: bold; }
        .form-group { margin-bottom: 1rem; }
        .error { color: #c00; }
        .versions { margin-top: 2rem; }
        .versions h3 { font-size: 1.1rem; }
        .versions ul { padding-left: 1.5rem; }
        .versions li { margin-bottom: 0.3rem; }"]]
     [:body
      [:nav
       [:div
        [:a {:href "/"} "Blog"]
        (when logged-in?
          [:a {:href "/articles/new"} "New Article"])]
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

(defn article-page [{:keys [article versions logged-in?]}]
  (let [{:keys [article_id title content created_at]} article]
    (layout {:title title :logged-in? logged-in?}
      [:article
       [:h1 (hu/escape-html title)]
       [:span.article-date created_at]
       (when logged-in?
         [:span " " [:a.btn.btn-small {:href (str "/articles/" article_id "/edit")} "Edit"]])
       [:div.article-content (hu/escape-html content)]]
      (when (and logged-in? (> (count versions) 1))
        [:div.versions
         [:h3 "Version history"]
         [:ul
          (for [v versions]
            [:li (:created_at v) " - " (hu/escape-html (:title v))])]]))))

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
      [:h1 (if new? "New Article" "Edit Article")]
      [:form {:method "POST" :action action}
       [:div.form-group
        [:label {:for "title"} "Title"]
        [:input {:type "text" :name "title" :id "title" :value (or (:title article) "") :required true}]]
       [:div.form-group
        [:label {:for "content"} "Content"]
        [:textarea {:name "content" :id "content"} (hu/escape-html (or (:content article) ""))]]
       [:button.btn {:type "submit"} (if new? "Create" "Save")]])))

(defn not-found-page [{:keys [logged-in?]}]
  (layout {:title "Not Found" :logged-in? logged-in?}
    [:h1 "Not Found"]
    [:p "The page you requested does not exist."]))
