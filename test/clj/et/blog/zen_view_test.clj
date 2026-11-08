(ns et.blog.zen-view-test
  "POST /article/:id/render, which Zen's view mode draws (specs/zen-view-mode.md):
  the editor's text, saved or not, rendered as the article page renders it, with
  data-pos on the blocks so that a click can find its place in the text."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [et.blog.render :as render]
            [et.blog.test-support :as t]))

(def ^:private quoted "Words worth quoting.")

;; Every kind of rewrite the article page makes: a footnote, a divider taken
;; out, a citation put in, a picture under blog-images/, an autolink opening in a
;; new tab and one to an .mp4 becoming a player.
(def ^:private text
  (str "Intro with **bold** and a [link](https://example.com/x). FOOTNOTE:a\n"
       "\n"
       "---\n"
       "\n"
       "Before CITE:1:2999-01-01 00:00:00:0:5 after\n"
       "\n"
       "## After\n"
       "\n"
       "- one\n"
       "- **two**\n"
       "\n"
       "![pic](blog-images/2/p.jpg)\n"
       "\n"
       "Autolinked <https://example.com/auto> and <https://example.com/clip.mp4>.\n"))

(def ^:private footnotes "FOOTNOTE:a The note itself.")

(defn- without-positions [html]
  (str/replace html #" data-pos=\"\d+-\d+\"" ""))

(defn- positions
  "[tag start end] for every element that says where it came from."
  [html]
  (for [[_ tag start end] (re-seq #"<(\w+)[^>]* data-pos=\"(\d+)-(\d+)\"" html)]
    [tag (parse-long start) (parse-long end)]))

(defn- with-image-base [f]
  (let [before @@#'render/image-base-url]
    (render/set-image-base-url! "https://img.example")
    (try (f) (finally (render/set-image-base-url! before)))))

(defn- setup
  "Article 1 to be quoted, article 2 holding `text` as saved."
  [app token]
  (t/POST app "/article" (t/article-params {"title" "Quoted" "content" quoted}) token)
  (t/POST app "/article" (t/article-params {"title" "Zen" "content" text "footnotes" footnotes}) token))

(deftest render-is-the-owners-alone
  (let [app (t/make-app)
        token (t/login app)]
    (setup app token)
    (let [resp (t/POST app "/article/2/render" {"content" text "footnotes" footnotes})]
      (is (= 302 (:status resp)))
      (is (= "/login" (t/redirect-location resp)))
      (is (not (str/includes? (str (:body resp)) "Intro with"))
          "nothing of the text comes back to a visitor"))))

(deftest render-is-the-article-pages-rendering
  (with-image-base
    (fn []
      (let [app (t/make-app)
            token (t/login app)
            _ (setup app token)
            resp (t/POST app "/article/2/render" {"content" text "footnotes" footnotes} token)
            html (:body resp)
            page (:body (t/GET app "/article/2" token))]
        (is (= 200 (:status resp)))
        (is (str/includes? (str (get-in resp [:headers "Content-Type"])) "text/html"))
        (is (str/includes? page (without-positions html))
            "less its data-pos, it is what the article page shows for the same text")
        (testing "the article page's rewrites are all in it"
          (is (str/includes? html "<sup><a href=\"#fn-1\">[1]</a></sup>") "footnotes numbered")
          (is (str/includes? html "The note itself.") "from the footnotes field")
          (is (str/includes? html "Words") "the citation quoted")
          (is (str/includes? html "src=\"https://img.example/blog-images/2/p.jpg\"")
              "a picture through the base URL")
          (is (not (str/includes? html "<hr")) "the divider taken out")
          (is (str/includes? html "target=\"_blank\"") "links open in a new tab")
          (is (str/includes? html "<a href=\"https://example.com/auto\" target=\"_blank\"")
              "an autolink too, whose data-pos flexmark writes before href")
          (is (str/includes? html "<video") "an autolinked .mp4 is a player"))
        (testing "the page itself carries no positions"
          (is (not (str/includes? page "data-pos"))))))))

(deftest render-renders-the-text-it-is-sent-not-the-saved-one
  (let [app (t/make-app)
        token (t/login app)]
    (setup app token)
    (let [html (:body (t/POST app "/article/2/render"
                        {"content" "Not saved *yet*" "footnotes" ""} token))]
      (is (str/includes? html "Not saved <em"))
      (is (not (str/includes? html "Intro with"))))))

(deftest render-says-where-each-block-came-from
  (let [app (t/make-app)
        token (t/login app)
        _ (setup app token)
        html (:body (t/POST app "/article/2/render" {"content" text "footnotes" footnotes} token))
        at (positions html)
        source (fn [tag] (some (fn [[t s e]] (when (= t tag) (subs text s e))) at))
        cite (str/index-of text "CITE:")]
    (testing "counted in the text as sent, across the divider taken out and the citation put in"
      (is (str/starts-with? (source "p") "Intro with"))
      (is (= "bold" (source "strong")))
      (is (= "[link](https://example.com/x)" (source "a")))
      (is (= "After" (source "h2")))
      (is (= "- one\n" (source "li")))
      (is (= "![pic](blog-images/2/p.jpg)" (source "img"))))
    (testing "the paragraph around the citation, on either side of it"
      (is (some (fn [[t s e]] (and (= t "p") (= "Before " (subs text s e)))) at))
      (is (some (fn [[t s e]] (and (= t "p") (= "after\n" (subs text s e)))) at)))
    (testing "the quoted words are not in the text: they point at the CITE: that made them"
      (let [quote-p (some (fn [[t s e]] (when (and (= t "p") (= s cite)) [s e])) at)]
        (is (= [cite cite] quote-p))))
    (testing "no position in the footnotes, which are another field"
      (let [section (subs html (str/index-of html "<section class=\"footnotes\""))]
        (is (not (str/includes? section "data-pos")))))))
