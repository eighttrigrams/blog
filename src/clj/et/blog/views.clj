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
        .article-list { list-style: none; padding: 0; }
        .article-list li { margin-bottom: 1.5rem; padding-bottom: 1.5rem; border-bottom: 1px solid rgba(0,0,0,0.08); }
        .article-list li:last-child { border-bottom: none; }
        .article-list a { color: rgba(0,0,0,0.8); }
        .article-list a:hover h2 { color: #FD5353; }
        .article-date { color: rgba(0,0,0,0.4); font-size: 0.9rem; }
        .article-content { white-space: pre-wrap; margin-top: 1.5rem; }
        .btn { display: inline-block; padding: 0.4rem 1rem; background: #FD5353; color: #fff; text-decoration: none; border: none; border-radius: 3px; cursor: pointer; font-size: 1rem; font-family: inherit; }
        .btn:hover { background: #e04848; text-decoration: none; }
        .btn-small { padding: 0.2rem 0.6rem; font-size: 0.85rem; }
        input[type=text], input[type=password], textarea { width: 100%; padding: 0.6rem; font-size: 1.125rem; font-family: inherit; border: 1px solid rgba(0,0,0,0.15); border-radius: 3px; }
        input[type=text]:focus, input[type=password]:focus, textarea:focus { outline: none; border-color: #FD5353; }
        textarea { min-height: 300px; resize: vertical; line-height: 1.8; }
        label { display: block; margin-bottom: 0.3rem; font-weight: 600; }
        .form-group { margin-bottom: 1.25rem; }
        .error { color: #FD5353; }
        .versions { margin-top: 2.5rem; border-top: 1px solid rgba(0,0,0,0.08); padding-top: 1.5rem; }
        .versions h3 { font-size: 1rem; font-weight: 600; color: rgba(0,0,0,0.6); }
        .versions ul { padding-left: 1.5rem; }
        .versions li { margin-bottom: 0.3rem; font-size: 0.95rem; color: rgba(0,0,0,0.6); }"]]
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

(defn article-page [{:keys [article versions logged-in? current-version]}]
  (let [{:keys [article_id title content created_at]} article]
    (layout {:title title :logged-in? logged-in?}
      [:article
       [:h1 (hu/escape-html title)]
       [:span.article-date created_at]
       (when logged-in?
         [:span " " [:a.btn.btn-small {:href (str "/articles/" article_id "/edit")} "Edit"]])
       [:div.article-content (hu/escape-html content)]]
      (when (> (count versions) 1)
        [:div.versions
         [:h3 "Version history"]
         [:ul
          (for [v versions]
            (let [active? (= (:created_at v) (or current-version created_at))]
              [:li
               (if active?
                 [:strong (:created_at v) " - " (hu/escape-html (:title v))]
                 [:a {:href (str "/articles/" article_id "?v=" (hu/url-encode (:created_at v)))}
                  (:created_at v) " - " (hu/escape-html (:title v))])]))]]))))

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
