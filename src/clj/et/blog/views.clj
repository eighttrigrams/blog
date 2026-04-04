(ns et.blog.views
  (:require [clojure.string :as str]
            [hiccup2.core :as h]
            [hiccup.util :as hu]
            [et.blog.render :as render]
            [et.blog.util :refer [human-date human-datetime]]))

(defn layout [{:keys [title logged-in?]} & body]
  (str
   (h/html
    [:html {:lang "en"}
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:title (if title (str title " - Blog") "Blog")]
      [:link {:rel "icon" :type "image/x-icon" :href "/favicon.ico"}]
      [:link {:rel "icon" :type "image/png" :sizes "32x32" :href "/favicon-32x32.png"}]
      [:link {:rel "icon" :type "image/png" :sizes "16x16" :href "/favicon-16x16.png"}]
      [:link {:rel "apple-touch-icon" :sizes "180x180" :href "/apple-touch-icon.png"}]
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
        nav a, nav a:visited { color: rgba(0,0,0,0.8); margin-right: 1.25rem; }
        nav a:hover { color: #FD5353; }
        .nav-right { display: flex; align-items: center; gap: 0.75rem; }
        .action-link, .action-link:visited { color: #FD5353; }
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
        .article-list-link, .article-list-link:visited { color: rgba(0,0,0,0.8); text-decoration: none; }
        .article-list-link:hover { color: #FD5353; text-decoration: underline; }
        .topic-link { color: rgba(0,0,0,0.5); text-decoration: none; }
        .topic-link:hover { color: #FD5353; text-decoration: underline; }
        .topic-link:visited { color: rgba(0,0,0,0.5); }
        .article-date { color: rgba(0,0,0,0.4); font-size: 0.9rem; }
        .article-preview { max-width: 300px; height: auto; margin-top: 0.5rem; display: block; }
        .article-version-info { color: rgba(0,0,0,0.4); font-size: 0.85rem; margin: 0 0 0.3rem 0; }
        .article-summary { color: rgba(0,0,0,0.5); font-size: 0.95rem; margin-top: 0.3rem; }
        .article-summary p:first-child { margin-top: 0; }
        .article-row { display: grid; grid-template-columns: 1fr 1fr; grid-template-rows: auto auto; gap: 0 1rem; margin-top: 0.5rem; }
        .article-row .article-version-info { grid-column: 2; grid-row: 1; margin: 0; }
        .article-row-img { grid-column: 1; grid-row: 1 / 3; }
        .article-row .article-preview { max-width: 100%; margin-top: 0; }
        .article-row .article-summary { grid-column: 2; grid-row: 2; }
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
        .article-preamble { margin-top: 1.5rem; font-style: italic; color: rgba(0,0,0,0.6); }
        .article-content { margin-top: 1.5rem; }
        .article-content img { max-width: 100%; height: auto; }
        .article-content blockquote { border-left: 3px solid rgba(0,0,0,0.15); margin: 1rem 0; padding: 0.5rem 1rem; color: rgba(0,0,0,0.6); }
        .article-content code { background: rgba(0,0,0,0.05); padding: 0.15rem 0.4rem; border-radius: 3px; font-size: 0.9em; }
        .article-content pre code { display: block; padding: 1rem; overflow-x: auto; }
        .article-section { margin-top: 2rem; border-top: 1px solid rgba(0,0,0,0.08); padding-top: 1rem; }
        .article-section h3 { font-size: 1rem; font-weight: 600; font-style: italic; color: rgba(0,0,0,0.65); margin-bottom: 0; }
        .footnotes { margin-top: 2rem; border-top: 1px solid rgba(0,0,0,0.08); padding-top: 1rem; padding-bottom: 1rem; border-bottom: 1px solid rgba(0,0,0,0.08); }
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
        .confirm-box .confirm-actions { display: flex; gap: 0.75rem; margin-top: 1rem; }
        @media (max-width: 600px) {
          .article-row { grid-template-columns: 1fr; grid-template-rows: auto; }
          .article-row .article-version-info { grid-column: 1; grid-row: 1; }
          .article-row-img { grid-column: 1; grid-row: 2; }
          .article-row .article-summary { grid-column: 1; grid-row: 3; }
        }"]]
     [:body
      [:nav
       [:div
        [:a {:href "/posts"} "Posts"]
        [:a {:href "/"} "Articles"]
        (when logged-in?
          [:a {:href "/article/drafts"} "Drafts"])]
       [:div.nav-right
        [:a.feed-icon {:href "/feed/articles.xml" :title "Articles feed"}
         (h/raw "<svg width=\"14\" height=\"14\" viewBox=\"0 0 256 256\"><circle cx=\"68\" cy=\"189\" r=\"28\" fill=\"currentColor\"/><path d=\"M160 213h-34a89 89 0 0 0-89-89V90a123 123 0 0 1 123 123z\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"32\"/><path d=\"M220 213h-34a149 149 0 0 0-149-149V30a183 183 0 0 1 183 183z\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"32\"/></svg>")]
        [:a.feed-icon {:href "/feed/posts.xml" :title "Posts feed"}
         (h/raw "<svg width=\"14\" height=\"14\" viewBox=\"0 0 256 256\"><circle cx=\"68\" cy=\"189\" r=\"28\" fill=\"currentColor\"/><path d=\"M160 213h-34a89 89 0 0 0-89-89V90a123 123 0 0 1 123 123z\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"32\"/><path d=\"M220 213h-34a149 149 0 0 0-149-149V30a183 183 0 0 1 183 183z\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"32\"/></svg>")]
        [:a.feed-icon {:href "/email" :title "Subscribe via email"}
         (h/raw "<svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><rect x=\"2\" y=\"4\" width=\"20\" height=\"16\" rx=\"2\"/><path d=\"M22 4L12 13 2 4\"/></svg>")]
        [:a.feed-icon {:href "https://github.com/eighttrigrams" :title "GitHub" :target "_blank" :rel "noopener"}
         (h/raw "<svg width=\"14\" height=\"14\" viewBox=\"0 0 16 16\" fill=\"currentColor\"><path d=\"M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0016 8c0-4.42-3.58-8-8-8z\"/></svg>")]
        (when logged-in?
          [:a {:href "/logout"} "Logout"])]]
      body]])))

(defn home-page [{:keys [articles logged-in? topic]}]
  (layout {:title nil :logged-in? logged-in?}
    [:h1 [:a {:href "/" :class "article-list-link"} "Articles"]]
    [:div {:style "margin-bottom: 1.5rem; display: flex; gap: 1rem;"}
     (for [[label val] [["SWE" "swe"] ["Modelling" "modelling"] ["Thoughts" "thoughts"]]]
       (if (= topic val)
         [:strong {:style "color: rgba(0,0,0,0.8);"} label]
         [:a.topic-link {:href (str "/articles?topic=" val)} label]))
]
    (if (seq articles)
      [:ul.article-list
       (for [{:keys [article_id title subtitle preview_image abstract latest-version latest-published-at]} articles]
         [:li
          [:a {:href (str "/article/" article_id)}
           [:h2 title]]
          (when (and subtitle (not= subtitle ""))
            [:p.subtitle subtitle])
          (let [has-img (and preview_image (not= preview_image ""))
                has-abs (and abstract (not= abstract ""))
                has-ver (and latest-version latest-published-at)]
            (when (or has-img has-abs has-ver)
              [:div.article-row
               (when has-ver
                 [:p.article-version-info
                  (str "Latest version " latest-version " published on " (human-date latest-published-at))])
               (when has-img
                 [:div.article-row-img
                  [:img.article-preview {:src preview_image :alt title}]])
               (when has-abs
                 [:div.article-summary (h/raw (render/markdown->html abstract))])]))])]
      [:p "No articles yet."])))

(defn drafts-page [{:keys [articles logged-in?]}]
  (layout {:title "Drafts" :logged-in? logged-in?}
    [:div
     [:p [:a.action-link {:href "/article/new"} "New Article"]]
     (if (seq articles)
       [:ul.article-list
        (for [{:keys [article_id title subtitle]} articles]
          [:li
           [:a {:href (str "/article/" article_id)}
            [:h2 title]]
           (when (and subtitle (not= subtitle ""))
             [:p.subtitle subtitle])])]
       [:p "No drafts."])]))

(defn email-page [{:keys [logged-in? notice error messages subscribers]}]
  (layout {:title "Email updates" :logged-in? logged-in?}
    [:h1 "Email updates"]
    [:p "Get notified when new articles are published."]
    (when notice
      [:p {:style "font-weight: 600;"} notice])
    (when error
      [:p.error error])
    [:form {:method "POST" :action "/email" :style "max-width: 400px;"}
     [:div {:style "margin-bottom: 0.75rem; display: flex; gap: 1rem;"}
      [:label [:input {:type "radio" :name "action" :value "subscribe" :checked true}] " Subscribe"]
      [:label [:input {:type "radio" :name "action" :value "unsubscribe"}] " Unsubscribe"]]
     [:div {:style "display: flex; gap: 0.5rem;"}
      [:input {:type "email" :name "email" :placeholder "you@example.com" :required true :style "flex: 1;"}]
      [:button.btn {:type "submit"} "Submit"]]]
    [:div {:style "margin-top: 3rem; border-top: 1px solid rgba(0,0,0,0.08); padding-top: 1.5rem;"}
     [:h2 "Leave a message"]
     (when error
       [:p.error error])
     [:form {:method "POST" :action "/email/message" :style "max-width: 400px;"}
      [:div {:style "margin-bottom: 0.5rem;"}
       [:input {:type "email" :name "email" :placeholder "you@example.com" :required true}]]
      [:div {:style "margin-bottom: 0.5rem;"}
       [:textarea {:name "message" :placeholder "Your message..." :required true :style "min-height: 120px;"}]]
      [:button.btn {:type "submit"} "Send"]]]
    (when (and logged-in? (seq messages))
      [:div {:style "margin-top: 3rem; border-top: 1px solid rgba(0,0,0,0.08); padding-top: 1.5rem;"}
       [:h2 "Messages"]
       (for [{:keys [email message created_at]} messages]
         [:div {:style "margin-bottom: 1.5rem; padding-bottom: 1.5rem; border-bottom: 1px solid rgba(0,0,0,0.08);"}
          [:p {:style "color: rgba(0,0,0,0.5); font-size: 0.85rem; margin: 0;"} (str email " \u2014 " created_at)]
          [:p {:style "margin: 0.3rem 0 0 0;"} message]])])
    (when (and logged-in? (seq subscribers))
      [:div {:style "margin-top: 3rem; border-top: 1px solid rgba(0,0,0,0.08); padding-top: 1.5rem;"}
       [:details
        [:summary {:style "cursor: pointer; font-size: 1.5rem; font-weight: 600;"} (str "Subscribers (" (count subscribers) ")")]
        [:ul {:style "margin-top: 0.75rem;"}
         (for [{:keys [email created_at]} subscribers]
           [:li {:style "margin-bottom: 0.3rem; font-size: 0.95rem; color: rgba(0,0,0,0.7);"}
            (str email " \u2014 " created_at)])]]])))

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

(defn- reply-entry [logged-in? {:keys [id display_name body created_at]}]
  [:div {:style "margin-bottom: 1rem; padding-bottom: 1rem; border-bottom: 1px solid rgba(0,0,0,0.05);"}
   [:p {:style "margin: 0; color: rgba(0,0,0,0.5); font-size: 0.9rem;"}
    [:strong {:style "color: rgba(0,0,0,0.8);"} display_name]
    " replied on " (human-datetime created_at)
    (when logged-in?
      (list " " [:a.btn.btn-small.btn-danger {:href (str "/replies/" id "/delete")} "Delete"]))]
   [:div.article-content {:style "margin-top: 0.3rem;"} (h/raw (render/markdown->html body))]])

(defn- comment-entry [article_id versions logged-in? {:keys [id display_name body article_version created_at replies]}]
  [:div {:style "margin-bottom: 1.5rem; padding-bottom: 1.5rem; border-bottom: 1px solid rgba(0,0,0,0.08);"}
   [:p {:style "margin: 0; color: rgba(0,0,0,0.5); font-size: 0.9rem;"}
    [:a {:href (str "/article/" article_id "/version/" article_version "/comment/" id)
         :style "color: rgba(0,0,0,0.5); text-decoration: none;"} "#"]
    " " [:strong {:style "color: rgba(0,0,0,0.8);"} display_name]
    " commented"
    (when (> (count versions) 1)
      (list " on " [:span.version-badge (str "v" article_version)] " of the article,"))
    " on " (human-datetime created_at)
    (when logged-in?
      (list " " [:a.btn.btn-small.btn-danger {:href (str "/comments/" id "/delete")} "Delete"]))]
   [:div.article-content {:style "margin-top: 0.3rem;"} (h/raw (render/markdown->html body))]
   (when (seq replies)
     [:div {:style "margin-left: 2rem; margin-top: 1rem; border-left: 2px solid rgba(0,0,0,0.08); padding-left: 1rem;"}
      (for [r replies] (reply-entry logged-in? r))])])

(defn- comments-section [article_id version versions comments logged-in?]
  (when (and version (pos? version))
    [:div.article-section
     [:h3 {:style "font-style: normal; margin-bottom: 1.5rem;"}
      [:a {:href (str "/article/" article_id "/version/" version "/comments")
           :style "color: rgba(0,0,0,0.65); text-decoration: none;"}
       "#"]
      " Comments"]
     (when (seq comments)
       [:div (for [c comments] (comment-entry article_id versions logged-in? c))])
     [:a.action-link {:href (str "/article/" article_id "/version/" version "/comment")} "Leave a comment"]]))

(defn article-page [{:keys [article versions logged-in? current-version rendered-content rendered-addenda rendered-preamble comments]}]
  (let [{:keys [article_id title subtitle created_at version content]} article]
    (layout {:title title :logged-in? logged-in?}
      [:article
       [:h1 title]
       (when (and subtitle (not= subtitle ""))
         [:p.subtitle subtitle])
       [:p.word-count (str (word-count content) " words")]
       (if (> (count versions) 1)
         (version-nav "/article/" :article_id article (or current-version created_at) versions)
         [:div.version-nav [:span.article-date created_at]])
       (let [pub-versions (->> versions (map :version) (filter pos?) distinct sort vec)]
         (if (and version (pos? version) (> (count pub-versions) 1))
           (let [idx (.indexOf pub-versions version)
                 prev-ver (when (pos? idx) (nth pub-versions (dec idx)))
                 next-ver (when (< idx (dec (count pub-versions))) (nth pub-versions (inc idx)))]
             [:div.version-nav
              (if prev-ver
                [:a.version-arrow {:href (str "/article/" article_id "/version/" prev-ver)} "\u2190"]
                [:span.version-arrow.disabled "\u2190"])
              [:span.version-badge (str "v" version)]
              (if next-ver
                [:a.version-arrow {:href (str "/article/" article_id "/version/" next-ver)} "\u2192"]
                [:span.version-arrow.disabled "\u2192"])])
           (if (and version (pos? version))
             [:span.version-badge (str "v" version)]
             (when logged-in? [:span.version-badge.draft "draft"]))))
       (when logged-in?
         [:span " " [:a.btn.btn-small {:href (str "/article/" article_id "/edit")} "Edit"]])
       (when rendered-preamble
         [:div.article-preamble (h/raw rendered-preamble)])
       [:div.article-content (h/raw rendered-content)]
       (when rendered-addenda
         [:div {:style "margin-top: 2rem;"}
          [:h3 {:style "font-size: 1rem; font-weight: 600; font-style: italic; color: rgba(0,0,0,0.65); margin-bottom: 0;"} "Addenda:"]
          [:div.article-content (h/raw rendered-addenda)]])]
      (comments-section article_id version versions comments logged-in?))))

(defn comment-form-page [{:keys [article logged-in? error]}]
  (let [{:keys [article_id version title]} article]
    (layout {:title (str "Comment on " title) :logged-in? logged-in?}
      [:h1 "Leave a comment"]
      [:p "On: " [:strong title] " (v" version ")"]
      (when error
        [:p.error error])
      [:form {:method "POST" :action (str "/article/" article_id "/version/" version "/comment") :style "max-width: 500px;"}
       [:div.form-group
        [:label {:for "display-name"} "Display name"]
        [:input {:type "text" :name "display-name" :id "display-name" :required true}]]
       [:div.form-group
        [:label {:for "email"} "Email (won't be displayed)"]
        [:input {:type "email" :name "email" :id "email" :required true}]]
       [:div.form-group
        [:label {:for "body"} "Comment"]
        [:textarea {:name "body" :id "body" :required true :style "min-height: 150px;"}]]
       [:button.btn {:type "submit"} "Submit"]])))

(defn reply-form-page [{:keys [comment article logged-in? error]}]
  (let [{:keys [id display_name body]} comment
        {:keys [title]} article]
    (layout {:title (str "Reply to " display_name) :logged-in? logged-in?}
      [:h1 "Reply"]
      [:p "To " [:strong display_name] "'s comment on: " [:strong title]]
      [:blockquote {:style "border-left: 3px solid rgba(0,0,0,0.15); margin: 1rem 0; padding: 0.5rem 1rem; color: rgba(0,0,0,0.6);"}
       (h/raw (render/markdown->html body))]
      (when error
        [:p.error error])
      [:form {:method "POST" :action (str "/comments/" id "/reply") :style "max-width: 500px;"}
       [:div.form-group
        [:label {:for "display-name"} "Display name"]
        [:input {:type "text" :name "display-name" :id "display-name" :required true}]]
       [:div.form-group
        [:label {:for "email"} "Email (won't be displayed)"]
        [:input {:type "email" :name "email" :id "email" :required true}]]
       [:div.form-group
        [:label {:for "body"} "Reply"]
        [:textarea {:name "body" :id "body" :required true :style "min-height: 150px;"}]]
       [:button.btn {:type "submit"} "Submit"]])))

(defn comment-page [{:keys [article comment rendered-body replies logged-in?]}]
  (let [{:keys [article_id title version]} article
        {:keys [id display_name created_at article_version]} comment]
    (layout {:title (str "# " title) :logged-in? logged-in?}
      [:article
       [:h1 (str "# " title)]
       [:p {:style "color: rgba(0,0,0,0.5); font-size: 0.9rem;"}
        [:strong {:style "color: rgba(0,0,0,0.8);"} display_name]
        " commented on "
        [:a {:href (str "/article/" article_id "/version/" article_version)} (str "v" article_version)]
        ", " (human-datetime created_at)
        (when logged-in?
          (list " " [:a.btn.btn-small.btn-danger {:href (str "/comments/" id "/delete")} "Delete"]))]
       [:div.article-content (h/raw rendered-body)]
       (when (seq replies)
         [:div {:style "margin-left: 2rem; margin-top: 1rem; border-left: 2px solid rgba(0,0,0,0.08); padding-left: 1rem;"}
          (for [r replies] (reply-entry logged-in? r))])
       [:p {:style "margin-top: 1.5rem;"} [:a.action-link {:href (str "/comments/" id "/reply")} "Reply"]]])))

(defn comments-list-page [{:keys [article comments version logged-in?]}]
  (let [{:keys [article_id title]} article]
    (layout {:title (str "Comments on " title) :logged-in? logged-in?}
      [:h1 (str "Comments on \"" title "\"")]
      (when version
        (list
          [:p [:a {:href (str "/article/" article_id "/comments")} "All comments"]]
          [:p "Version " version]))
      (if (seq comments)
        [:ul {:style "list-style: none; padding: 0;"}
         (for [{:keys [id display_name body article_version created_at replies]} comments]
           [:li {:style "margin-bottom: 1.5rem; padding-bottom: 1.5rem; border-bottom: 1px solid rgba(0,0,0,0.08);"}
            [:p {:style "margin: 0; color: rgba(0,0,0,0.5); font-size: 0.9rem;"}
             [:strong {:style "color: rgba(0,0,0,0.8);"} display_name]
             " on "
             [:a {:href (str "/article/" article_id "/version/" article_version "/comment/" id)}
              (str "v" article_version)]
             ", " (human-datetime created_at)
             (when logged-in?
               (list " " [:a.btn.btn-small.btn-danger {:href (str "/comments/" id "/delete")} "Delete"]))]
            [:div.article-content {:style "margin-top: 0.3rem;"} (h/raw (render/markdown->html body))]
            (when (seq replies)
              [:div {:style "margin-left: 2rem; margin-top: 1rem; border-left: 2px solid rgba(0,0,0,0.08); padding-left: 1rem;"}
               (for [r replies] (reply-entry logged-in? r))])])]
        [:p "No comments yet."])
      (when version
        [:p [:a.action-link {:href (str "/article/" article_id "/version/" version "/comment")}
             "Leave a comment"]]))))

(defn confirm-delete-comment-page [{:keys [comment logged-in?]}]
  (layout {:title "Delete Comment" :logged-in? logged-in?}
    [:h1 "Delete Comment"]
    [:div.confirm-box
     [:p "Are you sure you want to delete this comment by " [:strong (:display_name comment)] "?"]
     [:div.article-content {:style "margin: 0.5rem 0;"} (h/raw (render/markdown->html (:body comment)))]
     [:form {:method "POST" :action (str "/comments/" (:id comment) "/delete")}
      [:div.form-group
       [:label {:for "reason"} "Reason (optional)"]
       [:textarea {:name "reason" :id "reason" :style "min-height: 80px;"}]]
      [:div.confirm-actions
       [:button.btn.btn-danger {:type "submit"} "Delete"]
       [:a.btn.btn-cancel {:href (str "/article/" (:article_id comment) "/version/" (:article_version comment))} "Cancel"]]]]))

(defn confirm-delete-reply-page [{:keys [reply comment logged-in?]}]
  (layout {:title "Delete Reply" :logged-in? logged-in?}
    [:h1 "Delete Reply"]
    [:div.confirm-box
     [:p "Are you sure you want to delete this reply by " [:strong (:display_name reply)] "?"]
     [:div.article-content {:style "margin: 0.5rem 0;"} (h/raw (render/markdown->html (:body reply)))]
     [:form {:method "POST" :action (str "/replies/" (:id reply) "/delete")}
      [:div.form-group
       [:label {:for "reason"} "Reason (optional)"]
       [:textarea {:name "reason" :id "reason" :style "min-height: 80px;"}]]
      [:div.confirm-actions
       [:button.btn.btn-danger {:type "submit"} "Delete"]
       [:a.btn.btn-cancel {:href (str "/article/" (:article_id comment) "/version/" (:article_version comment)
                                      "/comment/" (:id comment))} "Cancel"]]]]))

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
  (let [action (if new? "/article" (str "/article/" (:article_id article)))]
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
           [:a.btn.btn-small.btn-danger {:href (str "/article/" (:article_id article) "/delete")} "Delete"])]]
       [:div.form-group
        [:label {:for "title"} "Title"]
        [:input {:type "text" :name "title" :id "title" :value (or (:title article) "") :required true}]]
       [:div.form-group
        [:label {:for "subtitle"} "Subtitle"]
        [:input {:type "text" :name "subtitle" :id "subtitle" :value (or (:subtitle article) "")}]]
       [:div.form-group
        [:label {:for "preamble"} "Preamble"]
        [:input {:type "text" :name "preamble" :id "preamble" :value (or (:preamble article) "")}]]
       [:div.form-group
        [:label {:for "preview-image"} "Preview Image"]
        [:input {:type "text" :name "preview-image" :id "preview-image" :value (or (:preview_image article) "")}]]
       [:div.form-group
        [:label {:for "abstract"} "Abstract"]
        [:textarea {:name "abstract" :id "abstract" :style "min-height: 80px;"} (or (:abstract article) "")]]
       [:div.form-group
        [:label {:for "topics"} "Categories"]
        [:input {:type "text" :name "topics" :id "topics" :value (or (:topics article) "")}]]
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
    (when logged-in?
      [:p [:a.action-link {:href "/post/new"} "New Post"]])
    (if (seq posts)
      [:ul.post-list
       (for [{:keys [post_id created_at first_at rendered-content article-link resolved-image]} posts]
         [:li
          [:div.post-heading
           [:h2 [:a.post-permalink {:href (str "/post/" post_id)} "#"] " " (human-date (or first_at created_at))]
           (when logged-in?
             [:a.btn.btn-small {:href (str "/post/" post_id "/edit")} "Edit"])]
          [:div.article-content (h/raw rendered-content)]
          (when article-link
            (list
              (when resolved-image
                [:a {:href (str "/article/" (:article_id article-link))}
                 [:img.article-preview {:src resolved-image :alt (:title article-link)}]])
              [:p.post-article-link
               [:a {:href (str "/article/" (:article_id article-link) "/version/" (:article_version article-link))}
                (:title article-link)
                " \u2192"]]
              (when-let [sub (not-empty (:subtitle article-link))]
                [:p.subtitle sub])))])]
      [:p "No posts yet."])))

(defn post-page [{:keys [post versions logged-in? current-version rendered-content article-link resolved-image]}]
  (let [{:keys [post_id created_at]} post]
    (layout {:title "Post" :logged-in? logged-in?}
      [:article
       (if (and logged-in? (> (count versions) 1))
         (version-nav "/post/" :post_id post (or current-version created_at) versions)
         [:div.version-nav [:span.article-date created_at]])
       (when logged-in?
         [:span [:a.btn.btn-small {:href (str "/post/" post_id "/edit")} "Edit"]])
       [:div.article-content (h/raw rendered-content)]
       (when article-link
         (list
           (when resolved-image
             [:a {:href (str "/article/" (:article_id article-link))}
              [:img.article-preview {:src resolved-image :alt (:title article-link)}]])
           [:p.post-article-link
            [:a {:href (str "/article/" (:article_id article-link) "/version/" (:article_version article-link))}
             (:title article-link)
             " \u2192"]]
           (when-let [sub (not-empty (:subtitle article-link))]
             [:p.subtitle sub])))])))

(defn edit-post-page [{:keys [post logged-in? new?]}]
  (let [action (if new? "/posts" (str "/post/" (:post_id post)))]
    (layout {:title (if new? "New Post" "Edit Post") :logged-in? logged-in?}
      [:form {:method "POST" :action action}
       [:div.edit-heading
        [:h1 (if new? "New Post" "Edit Post")]
        [:div.edit-actions
         [:button.btn {:type "submit"} "Save"]
         (when-not new?
           [:a.btn.btn-small.btn-danger {:href (str "/post/" (:post_id post) "/delete")} "Delete"])]]
       [:div.form-group
        [:label {:for "image"} "Image"]
        [:input {:type "text" :name "image" :id "image" :value (or (:image post) "")}]]
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
      [:form {:method "POST" :action (str "/article/" (:article_id article) "/delete")}
       [:button.btn.btn-danger {:type "submit"} "Delete"]]
      [:a.btn.btn-cancel {:href (str "/article/" (:article_id article) "/edit")} "Cancel"]]]))

(defn confirm-delete-post-page [{:keys [post logged-in?]}]
  (layout {:title "Delete Post" :logged-in? logged-in?}
    [:h1 "Delete Post"]
    [:div.confirm-box
     [:p "Are you sure you want to delete the post from " (human-date (:created_at post)) "?"]
     [:div.confirm-actions
      [:form {:method "POST" :action (str "/post/" (:post_id post) "/delete")}
       [:button.btn.btn-danger {:type "submit"} "Delete"]]
      [:a.btn.btn-cancel {:href (str "/post/" (:post_id post) "/edit")} "Cancel"]]]))

(defn deleted-articles-page [{:keys [articles logged-in?]}]
  (layout {:title "Deleted Articles" :logged-in? logged-in?}
    [:h1 "Deleted Articles"]
    (if (seq articles)
      [:ul.article-list
       (for [{:keys [article_id title created_at]} articles]
         [:li
          [:h2 title]
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
