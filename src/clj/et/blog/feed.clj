(ns et.blog.feed
  (:require [clojure.string :as str]
            [hiccup.util :as hu]))

(defn- xml-escape [s]
  (when s
    (-> s
        (str/replace "&" "&amp;")
        (str/replace "<" "&lt;")
        (str/replace ">" "&gt;")
        (str/replace "\"" "&quot;"))))

(defn- iso-date [datetime-str]
  (when datetime-str
    (str (str/replace datetime-str " " "T") "Z")))

(defn atom-feed [{:keys [title feed-url site-url posts article-links rendered-posts]}]
  (let [updated (or (some-> (first posts) (get :first_at (get (first posts) :created_at)) iso-date)
                    "1970-01-01T00:00:00Z")]
    (str
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
      "<feed xmlns=\"http://www.w3.org/2005/Atom\">\n"
      "  <title>" (xml-escape title) "</title>\n"
      "  <link href=\"" (xml-escape feed-url) "\" rel=\"self\"/>\n"
      "  <link href=\"" (xml-escape site-url) "\"/>\n"
      "  <id>" (xml-escape feed-url) "</id>\n"
      "  <updated>" updated "</updated>\n"
      (str/join
        (map-indexed
          (fn [idx post]
            (let [post-id (:post_id post)
                  created (or (:first_at post) (:created_at post))
                  link (get article-links post-id)
                  post-url (if link
                             (str site-url "/articles/" (:article_id link) "/version/" (:article_version link))
                             (str site-url "/posts"))
                  content-html (nth rendered-posts idx "")]
              (str
                "  <entry>\n"
                "    <title>" (if link (xml-escape (:title link)) (str "Post " post-id)) "</title>\n"
                "    <link href=\"" (xml-escape post-url) "\"/>\n"
                "    <id>" (xml-escape (str site-url "/posts/" post-id)) "</id>\n"
                "    <updated>" (iso-date created) "</updated>\n"
                "    <content type=\"html\">" (xml-escape content-html) "</content>\n"
                "  </entry>\n")))
          posts))
      "</feed>\n")))

(defn articles-feed [{:keys [title feed-url site-url articles rendered-articles]}]
  (let [updated (or (some-> (first articles) :created_at iso-date)
                    "1970-01-01T00:00:00Z")]
    (str
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
      "<feed xmlns=\"http://www.w3.org/2005/Atom\">\n"
      "  <title>" (xml-escape title) "</title>\n"
      "  <link href=\"" (xml-escape feed-url) "\" rel=\"self\"/>\n"
      "  <link href=\"" (xml-escape site-url) "\"/>\n"
      "  <id>" (xml-escape feed-url) "</id>\n"
      "  <updated>" updated "</updated>\n"
      (str/join
        (map-indexed
          (fn [idx article]
            (let [{:keys [article_id title version created_at]} article
                  article-url (str site-url "/articles/" article_id "/version/" version)
                  content-html (nth rendered-articles idx "")]
              (str
                "  <entry>\n"
                "    <title>" (xml-escape (str title " (v" version ")")) "</title>\n"
                "    <link href=\"" (xml-escape article-url) "\"/>\n"
                "    <id>" (xml-escape (str site-url "/articles/" article_id "/v" version)) "</id>\n"
                "    <updated>" (iso-date created_at) "</updated>\n"
                "    <content type=\"html\">" (xml-escape content-html) "</content>\n"
                "  </entry>\n")))
          articles))
      "</feed>\n")))
