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
        .article-row { display: grid; grid-template-columns: 1fr 1fr; grid-template-rows: auto 1fr; gap: 0 1rem; margin-top: 0.5rem; align-items: start; }
        .article-row .article-version-info { grid-column: 2; grid-row: 1; margin: 0; }
        .article-row-img { grid-column: 1; grid-row: 1 / 3; }
        .article-row .article-preview { max-width: 100%; margin-top: 0; }
        .article-row .article-summary { grid-column: 2; grid-row: 2; }
        .article-row-no-img { grid-template-columns: 1fr; }
        .article-row-no-img .article-version-info { grid-column: 1; max-width: 66%; }
        .article-row-no-img .article-summary { grid-column: 1; max-width: 66%; }
        .post-list { list-style: none; padding: 0; }
        .post-list li { margin-bottom: 1.5rem; padding-bottom: 1.5rem; border-bottom: 1px solid rgba(0,0,0,0.08); }
        .post-list li:last-child { border-bottom: none; }
        .post-list a { color: rgba(0,0,0,0.8); }
        .post-list a:hover { color: #FD5353; }
        .post-list .article-content a, .post-list .article-content a:visited { color: #1a0dab; }
        .post-list .article-content a:hover { text-decoration: underline; color: #1a0dab; }
        .post-heading { display: flex; justify-content: space-between; align-items: center; }
        .post-heading h2 { margin: 0; }
        .note-meta { font-size: 0.85rem; font-weight: 400; color: rgba(0,0,0,0.4); }
        .post-permalink { color: #1a0dab; text-decoration: none; }
        .post-permalink:hover { text-decoration: underline; }
        .post-article-link { margin-top: 0.75rem; font-weight: 600; }
        .article-preamble { margin-top: 1.5rem; font-style: italic; color: rgba(0,0,0,0.6); }
        .post-see-more summary { cursor: pointer; color: rgba(0,0,0,0.35); font-size: 0.85rem; list-style: none; }
        .post-see-more summary::-webkit-details-marker { display: none; }
        .post-see-more summary:hover { text-decoration: underline; }
        .post-see-more[open] summary { display: none; }
        .article-content { margin-top: 1.5rem; }
        .article-content img { max-width: 100%; height: auto; }
        .article-content blockquote { border-left: 3px solid rgba(0,0,0,0.15); margin: 1rem 0; padding: 0.5rem 1rem; color: rgba(0,0,0,0.6); }
        .article-content code { background: rgba(0,0,0,0.05); padding: 0.15rem 0.4rem; border-radius: 3px; font-size: 0.9em; }
        .article-content pre code { display: block; padding: 1rem; overflow-x: auto; }
        /* A Note is markdown like every other body here, so its line breaks are
           the renderer's job rather than white-space's. It sits closer to its
           heading than an article's content does, hence the override — which has
           to come after .article-content to win, both being one class. */
        .note-text { margin-top: 0.5rem; }
        .note-text > :first-child { margin-top: 0; }
        /* A Note being edited where it stands. The editor takes its look off the
           textarea it replaces — the bundle copies font, padding and border — so
           all that is wanted here is the drag handle: vertical only, as on every
           other textarea on the site. `overflow` is not decoration; `resize` does
           nothing on a box whose overflow is visible, and hidden rather than auto
           because the scrolling inside is CodeMirror's own to do. */
        .note-editor { margin-top: 0.5rem; }
        .note-editor .cm-editor { resize: vertical; overflow: hidden; }
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
        .edit-actions { display: flex; gap: 0.75rem; flex-shrink: 0; align-items: center; }
        /* A button that posts sits in its own form. Without this the form's
           default bottom margin is what gets centred, and the button rides
           half a margin above the plain links beside it. */
        .edit-actions form { margin: 0; }
        .version-nav { display: flex; align-items: center; gap: 0.5rem; }
        .version-line { display: flex; justify-content: space-between; align-items: center; gap: 1rem; }
        .version-line-right { margin-right: 16px; }
        a.version-badge, a.version-badge:visited { color: rgba(0,0,0,0.5); text-decoration: none; }
        a.version-badge:hover { color: #FD5353; text-decoration: none; }
        a.article-date, a.article-date:visited { color: rgba(0,0,0,0.4); text-decoration: none; }
        a.article-date:hover { color: #FD5353; text-decoration: none; }
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
        .symbol-palette { position: fixed; top: 50%; right: 0.75rem; transform: translateY(-50%); display: flex; flex-direction: column; gap: 0.4rem; z-index: 100; }
        .symbol-palette button { width: 2rem; height: 2rem; padding: 0; background: #fff; border: 1px solid rgba(0,0,0,0.8); border-radius: 2px; font-family: Spectral, Georgia, serif; font-size: 1.125rem; color: #000; cursor: pointer; line-height: 1; display: flex; align-items: center; justify-content: center; }
        .symbol-palette button:hover { background: rgba(0,0,0,0.05); }
        @media (max-width: 900px) { .symbol-palette { display: none; } }
        .btn-zen { background: rgba(0,0,0,0.55); }
        .btn-zen:hover { background: rgba(0,0,0,0.7); }
        /* Zen: a full-viewport writing surface for the Content field alone. The
           z-index stays under .symbol-palette (100) on purpose, so the quotes and
           the em-dash keep floating above it and stay reachable while writing. */
        #zen-overlay { position: fixed; inset: 0; background: #fff; z-index: 90; }
        #zen-column { max-width: 728px; height: 100%; margin: 0 auto; padding: 3.5rem 1.5rem 1.5rem; display: flex; }
        /* The mount only sizes the editor; the prose styling lives in the view's
           own theme in zen.js, which is where CodeMirror will honour it. */
        #zen-content { flex: 1; min-width: 0; min-height: 0; }
        #zen-content .cm-editor { height: 100%; }
        #zen-close { position: fixed; top: 0.25rem; left: 0.75rem; z-index: 91; padding: 0; background: none; border: none; font-family: inherit; font-size: 2.75rem; line-height: 1; color: rgba(0,0,0,0.25); cursor: pointer; }
        #zen-close:hover { color: #FD5353; }
        /* The save mark, after tracker's #save-flash / .save-flash-mark. 10000
           carries it above the overlay. */
        #save-flash { position: fixed; top: 10px; left: 0; right: 0; display: flex; justify-content: center; z-index: 10000; pointer-events: none; }
        .save-flash-mark { padding: 2px 16px; border-radius: 999px; background: rgba(52,199,89,0.18); color: #34c759; font-size: 1.9em; font-weight: 700; line-height: 1.3; text-shadow: 0 0 10px rgba(52,199,89,0.9); box-shadow: 0 0 12px rgba(52,199,89,0.6), 0 0 28px rgba(52,199,89,0.35); animation: fade-in-out 1.5s ease forwards; }
        .save-flash-mark.failed { background: rgba(220,53,69,0.18); color: #dc3545; text-shadow: 0 0 10px rgba(220,53,69,0.9); box-shadow: 0 0 12px rgba(220,53,69,0.6), 0 0 28px rgba(220,53,69,0.35); }
        @keyframes fade-in-out { 0% { opacity: 0; transform: translateY(-10px); } 10% { opacity: 1; transform: translateY(0); } 80% { opacity: 1; } 100% { opacity: 0; } }
        @media (max-width: 600px) {
          .article-row { grid-template-columns: 1fr; grid-template-rows: auto; }
          .article-row .article-version-info { grid-column: 1; grid-row: 1; }
          .article-row-img { grid-column: 1; grid-row: 2; }
          .article-row .article-summary { grid-column: 1; grid-row: 3; }
          .article-row-no-img .article-version-info,
          .article-row-no-img .article-summary { max-width: 100%; }
        }"]]
     [:body
      [:nav
       [:div
        [:a {:href "/posts"} "Posts"]
        [:a {:href "/articles"} "Articles"]
        (when logged-in?
          (list
            [:a {:href "/article/drafts"} "Drafts"]
            [:a {:href "/notes"} "Notes"]))]
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
          (list
            [:a {:href "/notes-users"} "Notes users"]
            [:a {:href "/logout"} "Logout"]))]]
      body]])))

;; What turns the textareas marked `:data-editor "1"` on a page into CodeMirror
;; editors with Daniel's IJKL bindings on them. Two tags, because the bundle is
;; 270KB: it is asked for by the pages that write, and by no others. That is also
;; why the marker exists at all rather than editors.js taking every textarea it
;; can find - the message box on /email and the comment and reply forms belong to
;; visitors, who did not ask for this keymap and should not download it.
;;
;; Kept out of `layout` deliberately, so adding a page never turns it on by
;; accident. A page that wants it says so, in both places.
(defn- editor-scripts []
  (list
    [:script {:src "/vendor/codemirror/codemirror.js"}]
    [:script {:src "/js/editors.js"}]))

(defn home-page [{:keys [articles logged-in? topic]}]
  (layout {:title nil :logged-in? logged-in?}
    [:h1 [:a {:href "/articles" :class "article-list-link"} "Articles"]]
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
              [:div {:class (str "article-row" (when-not has-img " article-row-no-img"))}
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
        (for [{:keys [article_id title subtitle preview_image]} articles]
          [:li
           [:a {:href (str "/article/" article_id)}
            [:h2 title]]
           (when (and subtitle (not= subtitle ""))
             [:p.subtitle subtitle])
           (when (and preview_image (not= preview_image ""))
             [:img.article-preview {:src preview_image :alt title}])])]
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

(defn note-text-fragment
  "A Note's text as the box shows it: rendered markdown, the inside of its
  `.note-text` block. A function rather than inline in `notes-page` because an
  inline save hands the very same thing back, so the block that was being edited
  can be read again without reloading the page — one renderer, one place."
  [text]
  (render/markdown->html text))

(defn notes-page [{:keys [logged-in? notes error]}]
  (layout {:title "Notes" :logged-in? logged-in?}
    [:h1 "Notes"]
    (when error
      [:p.error error])
    [:form {:method "POST" :action "/notes" :style "max-width: 500px;"}
     [:div.form-group
      [:label {:for "text"} "Text"]
      [:textarea {:name "text" :id "text" :data-editor "1"
                  :style "min-height: 100px;"}]]
     [:button.btn {:type "submit"} "Add Note"]]
    [:div {:style "margin-top: 3rem; border-top: 1px solid rgba(0,0,0,0.08); padding-top: 1.5rem;"}
     (if (seq notes)
       [:ul {:style "list-style: none; padding: 0;"}
        (for [{:keys [id text source created_at]} notes]
          [:li.note-item {:data-note-id id
                          :style "margin-bottom: 1.5rem; padding-bottom: 1.5rem; border-bottom: 1px solid rgba(0,0,0,0.08);"}
           ;; A Note has no title to head it with, so the heading row carries
           ;; when it arrived and from where, the way a Post's does.
           [:div.post-heading
            [:h2.note-meta
             (human-datetime created_at)
             (when-not (str/blank? source) (str " — " source))]
            ;; No Edit button: a click on the text below opens the Note where it
            ;; stands (notes.js), so Delete is the only thing left to click here.
            [:div.edit-actions
             [:form {:method "POST" :action (str "/notes/" id "/delete")}
              ;; Deleting a Note takes the row away — no tombstone, nothing
              ;; reads it again — so the click asks first. Blog's inline idiom,
              ;; as on Publish, rather than the confirm page an Article gets.
              [:button.btn.btn-small.btn-danger
               {:type "submit"
                :onclick "return confirm('Delete this Note? It is gone for good.');"}
               "Delete"]]]]
           [:div.note-text.article-content (h/raw (note-text-fragment text))]
           ;; The Note as it is written, waiting for the click that edits it.
           ;; Deliberately *not* marked `data-editor`: that marker means "mount at
           ;; load", and there is one of these per Note in the box. notes.js
           ;; mounts the one being edited, and only when it is being edited.
           ;; `min-height: 0` because the page-wide 300px is meant for a form
           ;; field the size of an Article, and CodeMirror takes the height it
           ;; finds on the textarea it replaces.
           [:div.note-editor {:style "display: none;"}
            [:textarea {:name "text" :style "min-height: 0;"} text]]])]
       [:p "The Notes box is empty."])]
    [:div#save-flash]
    (editor-scripts)
    [:script {:src "/js/notes.js"}]))

;; Reachable by URL only, now that the box edits a Note where it stands: it is
;; the one save path that needs no JavaScript, and the fallback if the inline
;; editor ever breaks. The POST it makes is the same one an inline save makes —
;; see `update-note-handler`.
(defn edit-note-page [{:keys [logged-in? note error]}]
  (layout {:title "Edit Note" :logged-in? logged-in?}
    (when error
      [:p.error error])
    [:form {:method "POST" :action (str "/notes/" (:id note)) :style "max-width: 500px;"}
     [:div.edit-heading
      [:h1 "Edit Note"]
      [:div.edit-actions
       [:button.btn {:type "submit"} "Save"]
       [:a.btn.btn-cancel {:href "/notes"} "Cancel"]]]
     [:div.form-group
      [:label {:for "text"} "Text"]
      [:textarea {:name "text" :id "text" :data-editor "1"
                  :style "min-height: 150px;"}
       (or (:text note) "")]]]
    (editor-scripts)))

(defn notes-users-page [{:keys [logged-in? notes-users created error]}]
  (layout {:title "Notes users" :logged-in? logged-in?}
    [:h1 "Notes users"]
    [:p "A notes user may deliver a Note to the "
     [:a {:href "/notes"} "Notes box"]
     " and nothing else. Reads of the public API need no credentials at all."]
    (when error
      [:p.error error])
    (when created
      [:div {:style "margin-bottom: 1.5rem; padding: 1rem; border: 1px solid #FD5353; border-radius: 5px;"}
       [:p {:style "margin: 0;"}
        "Created " [:strong (:name created)] ". Its password is shown here once and "
        "nowhere else — it is stored as a hash and cannot be recovered."]
       [:p {:style "margin: 0.5rem 0 0 0;"}
        [:code {:style "font-size: 1rem; word-break: break-all;"} (:password created)]]])
    [:form {:method "POST" :action "/notes-users" :style "max-width: 400px;"}
     [:div.form-group
      [:label {:for "name"} "Name"]
      [:input {:type "text" :name "name" :id "name" :required true}]]
     [:div.form-group
      [:label {:for "password"} "Password"]
      [:input {:type "text" :name "password" :id "password"}]
      [:p {:style "margin: 0.3rem 0 0 0; font-size: 0.9rem; color: rgba(0,0,0,0.5);"}
       "Leave empty to have one generated."]]
     [:button.btn {:type "submit"} "Create"]]
    [:div {:style "margin-top: 3rem; border-top: 1px solid rgba(0,0,0,0.08); padding-top: 1.5rem;"}
     (if (seq notes-users)
       [:ul {:style "list-style: none; padding: 0;"}
        (for [{:keys [id name created_at revoked_at]} notes-users]
          [:li {:style "margin-bottom: 0.75rem; display: flex; justify-content: space-between; align-items: baseline; gap: 1rem;"}
           [:span
            [:strong name]
            [:span {:style "color: rgba(0,0,0,0.4); font-size: 0.9rem;"}
             (str " — created " (human-date created_at))
             (when revoked_at (str ", revoked " (human-date revoked_at)))]]
           (when-not revoked_at
             [:form {:method "POST" :action (str "/notes-users/" id "/revoke")}
              [:button.btn.btn-small.btn-danger {:type "submit"} "Revoke"]])])]
       [:p "No notes users yet."])]))

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
     (if next-v
       [:a.version-arrow {:href (str base-path eid "/as-of/" (:created_at next-v))} "\u2192"]
       [:span.version-arrow.disabled "\u2192"])
     [:a.article-date {:href (str base-path eid "/as-of/" created_at)} created_at]]))

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
       (when (or logged-in? (not= article_id 36))
         (let [pub-versions (->> versions (map :version) (filter pos?) distinct sort vec)
               version-side (if (and version (pos? version) (> (count pub-versions) 1))
                              (let [idx (.indexOf pub-versions version)
                                    prev-ver (when (pos? idx) (nth pub-versions (dec idx)))
                                    next-ver (when (< idx (dec (count pub-versions))) (nth pub-versions (inc idx)))]
                                [:div.version-nav
                                 [:a.version-badge {:href (str "/article/" article_id "/version/" version)} (str "v" version)]
                                 (if prev-ver
                                   [:a.version-arrow {:href (str "/article/" article_id "/version/" prev-ver)} "\u2190"]
                                   [:span.version-arrow.disabled "\u2190"])
                                 (if next-ver
                                   [:a.version-arrow {:href (str "/article/" article_id "/version/" next-ver)} "\u2192"]
                                   [:span.version-arrow.disabled "\u2192"])])
                              (if (and version (pos? version))
                                [:a.version-badge {:href (str "/article/" article_id "/version/" version)} (str "v" version)]
                                (when logged-in? [:span.version-badge.draft "draft"])))
               time-side (if (> (count versions) 1)
                           (version-nav "/article/" :article_id article (or current-version created_at) versions)
                           [:div.version-nav [:span.article-date created_at]])]
           (list
             [:p.word-count (str (word-count content) " words")]
             [:div.version-line
              [:div.version-line-left version-side]
              [:div.version-line-right time-side]])))
       (when logged-in?
         [:span " " [:a.btn.btn-small {:href (str "/article/" article_id "/edit")} "Edit"]])
       (when rendered-preamble
         [:div.article-preamble (h/raw rendered-preamble)])
       [:div.article-content (h/raw rendered-content)]
       (when rendered-addenda
         [:div {:style "margin-top: 2rem;"}
          [:h3 {:style "font-size: 1rem; font-weight: 600; font-style: italic; color: rgba(0,0,0,0.65); margin-bottom: 0;"} "Addenda:"]
          [:div.article-content (h/raw rendered-addenda)]])]
      (when (or logged-in? (not= article_id 36))
        (comments-section article_id version versions comments logged-in?)))))

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
       [:textarea {:name "reason" :id "reason" :data-editor "1"
                   :style "min-height: 80px;"}]]
      [:div.confirm-actions
       [:button.btn.btn-danger {:type "submit"} "Delete"]
       [:a.btn.btn-cancel {:href (str "/article/" (:article_id comment) "/version/" (:article_version comment))} "Cancel"]]]]
    (editor-scripts)))

(defn confirm-delete-reply-page [{:keys [reply comment logged-in?]}]
  (layout {:title "Delete Reply" :logged-in? logged-in?}
    [:h1 "Delete Reply"]
    [:div.confirm-box
     [:p "Are you sure you want to delete this reply by " [:strong (:display_name reply)] "?"]
     [:div.article-content {:style "margin: 0.5rem 0;"} (h/raw (render/markdown->html (:body reply)))]
     [:form {:method "POST" :action (str "/replies/" (:id reply) "/delete")}
      [:div.form-group
       [:label {:for "reason"} "Reason (optional)"]
       [:textarea {:name "reason" :id "reason" :data-editor "1"
                   :style "min-height: 80px;"}]]
      [:div.confirm-actions
       [:button.btn.btn-danger {:type "submit"} "Delete"]
       [:a.btn.btn-cancel {:href (str "/article/" (:article_id comment) "/version/" (:article_version comment)
                                      "/comment/" (:id comment))} "Cancel"]]]]
    (editor-scripts)))

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

(defn edit-page [{:keys [article logged-in? new? error post-content version-published?]}]
  (let [action (if new? "/article" (str "/article/" (:article_id article)))]
    (layout {:title (if new? "New Article" "Edit Article") :logged-in? logged-in?}
      (when error
        [:p.error error])
      [:form {:method "POST" :action action}
       [:div.edit-heading
        [:h1 (if new? "New Article" "Edit Article")]
        [:div.edit-actions
         [:button.btn {:type "submit"} "Save"]
         (when (and (not new?)
                    (or (zero? (or (:version article) 0)) version-published?))
           [:button.btn.btn-publish {:type "submit" :name "save-version" :value "1"
                                     :onclick "return confirm('Save as a new version?');"}
            "Save new version"])
         (when (and (not new?)
                    (pos? (or (:version article) 0))
                    (not version-published?))
           [:button.btn.btn-publish {:type "submit" :name "publish" :value "1"} "Publish"])
         (when-not new?
           [:a.btn.btn-small.btn-danger {:href (str "/article/" (:article_id article) "/delete")} "Delete"])
         ;; Same guard as Delete: an id must exist for cmd+9 to have somewhere to
         ;; write. :type "button" because a bare button in a form submits it.
         (when-not new?
           [:button#zen-open.btn.btn-zen {:type "button"} "Zen"])]]
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
        [:textarea {:name "abstract" :id "abstract" :data-editor "1"
                    :style "min-height: 80px;"} (or (:abstract article) "")]]
       [:div.form-group
        [:label {:for "topics"} "Categories"]
        [:input {:type "text" :name "topics" :id "topics" :value (or (:topics article) "")}]]
       [:div.form-group
        [:label {:for "content"} "Content"]
        [:textarea {:name "content" :id "content" :data-editor "1"} (or (:content article) "")]]
       [:div.form-group
        [:label {:for "footnotes"} "Footnotes"]
        [:textarea {:name "footnotes" :id "footnotes" :data-editor "1"} (or (:footnotes article) "")]]
       [:div.form-group
        [:label {:for "addenda"} "Addenda"]
        [:textarea {:name "addenda" :id "addenda" :data-editor "1"} (or (:addenda article) "")]]
       (when (and (not new?)
                  (pos? (or (:version article) 0))
                  (not version-published?))
         [:details (when error {:open true})
          [:summary "Post content (required for publishing) - consider an article abstract"]
          [:div.form-group
           [:textarea {:name "post-content" :id "post-content" :data-editor "1"} (or post-content "")]]])]
      ;; Outside the <form> on purpose: nothing in here can be submitted, and the
      ;; textarea carries no name either, so it is never serialized. It has to be
      ;; in the DOM before the symbol-palette script below runs, which is what
      ;; lets the palette insert into it for free.
      (when-not new?
        (list
          [:div#zen-overlay {:style "display: none;"}
           [:button#zen-close {:type "button" :title "Leave Zen"} "\u00D7"]
           [:div#zen-column
            ;; The CodeMirror mount. A div, so there is nothing here that could
            ;; be serialized even if it were inside the form.
            [:div#zen-content]]]
          [:div#save-flash]))
      [:div.symbol-palette
       [:button {:type "button" :data-symbol "\u201C" :title "Opening double quote"} "\u201C"]
       [:button {:type "button" :data-symbol "\u201D" :title "Closing double quote"} "\u201D"]
       [:button {:type "button" :data-symbol "\u2018" :title "Opening single quote"} "\u2018"]
       [:button {:type "button" :data-symbol "\u2019" :title "Closing single quote"} "\u2019"]
       [:button {:type "button" :data-symbol "\u2014" :title "Em-dash"} "\u2014"]]
      [:script (h/raw "(function(){var last=null;document.querySelectorAll('textarea').forEach(function(t){t.addEventListener('focus',function(){last=t;});});document.querySelectorAll('.symbol-palette button').forEach(function(b){b.addEventListener('mousedown',function(e){e.preventDefault();});b.addEventListener('click',function(){if(!last)return;var s=b.getAttribute('data-symbol');var start=last.selectionStart,end=last.selectionEnd,v=last.value;last.value=v.slice(0,start)+s+v.slice(end);last.selectionStart=last.selectionEnd=start+s.length;last.focus();});});})();")]
      ;; Every field on this page is an editor, on a new article as much as on an
      ;; existing one. Zen is the exception rather than the rule now: it needs an
      ;; id to write cmd+9 saves to, which a new article has not got yet.
      (editor-scripts)
      (when-not new?
        [:script {:src "/js/zen.js"}]))))

(defn posts-page [{:keys [posts logged-in?]}]
  (layout {:title "Posts" :logged-in? logged-in?}
    [:h1 "Posts"]
    (when logged-in?
      [:p [:a.action-link {:href "/post/new"} "New Post"]])
    (if (seq posts)
      [:ul.post-list
       (for [{:keys [post_id created_at first_published_at above-html below-html truncated? article-link resolved-image has_published]} posts]
         [:li
          [:div.post-heading
           [:h2 [:a.post-permalink {:href (str "/post/" post_id)} "#"] " "
            (human-date (if logged-in? created_at (or first_published_at created_at)))
            (when (and logged-in? (some? has_published) (zero? has_published))
              [:span.version-badge.draft "draft"])]
           (when logged-in?
             [:a.btn.btn-small {:href (str "/post/" post_id "/edit")} "Edit"])]
          [:div.article-content
           (h/raw above-html)
           (when truncated?
             [:details.post-see-more
              [:summary "See more"]
              (h/raw below-html)])]
          (when (and resolved-image (not article-link))
            [:img.article-preview {:src resolved-image}])
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

(defn post-page [{:keys [post versions logged-in? current-version rendered-content article-link resolved-image
                         first-published-at last-published-at published-count]}]
  (let [{:keys [post_id created_at published_at]} post]
    (layout {:title "Post" :logged-in? logged-in?}
      [:article
       (let [modified? (and last-published-at first-published-at
                            (not= last-published-at first-published-at))]
         (list
           (when (and first-published-at (not article-link))
             [:h2 (human-date first-published-at)])
           (if (and logged-in? (> (count versions) 1))
             (version-nav "/post/" :post_id post (or current-version created_at) versions)
             [:div.version-nav
              [:span.article-date
               (cond
                 modified? (str "Last modified: " last-published-at)
                 first-published-at first-published-at
                 :else created_at)]])))
       (when (and logged-in? (nil? published_at) first-published-at
                  (neg? (compare created_at first-published-at)))
         [:span.version-badge.draft "PRE-PUBLISHED"])
       (when logged-in?
         [:span [:a.btn.btn-small {:href (str "/post/" post_id "/edit")} "Edit"]])
       [:div.article-content (h/raw rendered-content)]
       (when (and resolved-image (not article-link))
         [:img.article-preview {:src resolved-image}])
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

(defn edit-post-page [{:keys [post logged-in? new? has-published?]}]
  (let [action (if new? "/posts" (str "/post/" (:post_id post)))]
    (layout {:title (if new? "New Post" "Edit Post") :logged-in? logged-in?}
      [:form {:method "POST" :action action}
       [:div.edit-heading
        [:h1 (if new? "New Post" "Edit Post")]
        [:div.edit-actions
         [:button.btn {:type "submit"} "Save"]
         (when-not has-published?
           [:button.btn.btn-publish {:type "submit" :name "publish" :value "1"
                                     :onclick "return confirm('Publish this post?');"}
            "Publish"])
         (when-not new?
           [:a.btn.btn-small.btn-danger {:href (str "/post/" (:post_id post) "/delete")} "Delete"])]]
       [:div.form-group
        [:label {:for "image"} "Image"]
        [:input {:type "text" :name "image" :id "image" :value (or (:image post) "")}]]
       [:div.form-group
        [:label {:for "content"} "Content"]
        [:textarea {:name "content" :id "content" :data-editor "1"} (or (:content post) "")]]
      ]
      (editor-scripts))))

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
       (for [{:keys [created_at rendered-content]} posts]
         [:li
          [:div.post-heading
           [:h2 (human-date created_at)]]
          [:div.article-content (h/raw rendered-content)]])]
      [:p "No deleted posts."])))

(defn not-found-page [{:keys [logged-in?]}]
  (layout {:title "Not Found" :logged-in? logged-in?}
    [:h1 "Not Found"]
    [:p "The page you requested does not exist."]))
