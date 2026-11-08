(ns et.blog.render
  (:require [clojure.string :as str])
  (:import [com.vladsch.flexmark.html HtmlRenderer]
           [com.vladsch.flexmark.parser Parser]
           [com.vladsch.flexmark.util.data MutableDataSet]
           [com.vladsch.flexmark.ext.typographic TypographicExtension]))

(def ^:private md-options (doto (MutableDataSet.)
                            (.set HtmlRenderer/SOFT_BREAK "<br />\n")
                            (.set HtmlRenderer/ESCAPE_HTML true)
                            (.set Parser/EXTENSIONS [(TypographicExtension/create)])))
(def ^:private md-parser (.build (Parser/builder md-options)))
(def ^:private md-renderer (.build (HtmlRenderer/builder md-options)))

;; The same renderer, and every block, emphasis, link and image says which
;; characters of the text it came from: data-pos="start-end". Only for Zen's view
;; mode, which puts a click on the rendering back into the text (zen.js). The
;; attribute comes after href and src, so the rewrites below still find them.
(def ^:private md-renderer-with-positions
  (.build (HtmlRenderer/builder (doto (MutableDataSet. md-options)
                                  (.set HtmlRenderer/SOURCE_POSITION_ATTRIBUTE "data-pos")))))

(defn- externalize-links [html]
  (str/replace html #"<a href=\"(https?://[^\"]+)\""
    (fn [[_ url]]
      (str "<a href=\"" url "\" target=\"_blank\" rel=\"noopener\""))))

;; An autolink, <https://…>, gets its data-pos *before* href, where the rewrites
;; that look for `<a href="` would miss it. Behind href, as on every other link.
(defn- href-first [html]
  (str/replace html #"<a( data-pos=\"\d+-\d+\")( href=\"[^\"]*\")" "<a$2$1"))

(defn- render-markdown [^HtmlRenderer renderer text]
  (when (and text (not (str/blank? text)))
    (let [doc (.parse md-parser text)]
      (externalize-links (href-first (.render renderer doc))))))

(defn markdown->html [text]
  (render-markdown md-renderer text))

(defn- replace-mapped
  "str/replace with a function, and also a function that takes an offset in the
  result back to the offset in `text` it came from. An offset inside a
  replacement goes back to where what it replaced began: a citation's quoted
  words are not in the text, and a click on them lands on the CITE: that made them."
  [text pattern f]
  (let [m (re-matcher pattern text)
        sb (StringBuilder.)]
    (loop [from 0 edits []]
      (if (.find m)
        (let [start (.start m)
              at (.length (.append sb (subs text from start)))
              end (.length (.append sb ^String (f (re-groups m))))]
          (recur (.end m) (conj edits [at end start (.end m)])))
        [(str (.append sb (subs text from)))
         (fn [p]
           (if-let [[at end start stop] (last (take-while #(<= (first %) p) edits))]
             (if (< p end) start (+ stop (- p end)))
             p))]))))

(defn- positions->text
  "data-pos counts in the text flexmark was given. Count it in the text as written."
  [html back]
  (str/replace html #" data-pos=\"(\d+)-(\d+)\""
    (fn [[_ start end]]
      (str " data-pos=\"" (back (parse-long start)) "-" (back (parse-long end)) "\""))))

(def ^:private footnote-ref-pattern #"FOOTNOTE:([a-zA-Z0-9_-]+)")
(def ^:private cite-pattern #"CITE:(\d+):(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}):(\d+):(\d+)")

(defn- find-footnote-refs [content]
  (let [matches (re-seq footnote-ref-pattern content)]
    (vec (distinct (map second matches)))))

(defn- parse-footnote-defs [footnotes-text]
  (when (and footnotes-text (not (str/blank? footnotes-text)))
    (let [parts (str/split footnotes-text #"(?m)(?=^-?\s*FOOTNOTE:)")]
      (->> parts
           (keep (fn [part]
                   (let [trimmed (str/trim part)]
                     (when-let [[_ id text] (re-find #"^-?\s*FOOTNOTE:([a-zA-Z0-9_-]+)\s+([\s\S]*)" trimmed)]
                       [id (str/trim text)]))))
           (into {})))))

(defn- replace-footnote-refs [content ref-order def-map]
  (str/replace content footnote-ref-pattern
    (fn [[_ id]]
      (if-let [idx (get ref-order id)]
        (let [n (inc idx)]
          (if (contains? def-map id)
            (str "<sup><a href=\"#fn-" n "\">[" n "]</a></sup>")
            (str "<sup class=\"missing\">[MISSING: " id "]</sup>")))
        (str "<sup class=\"missing\">[MISSING: " id "]</sup>")))))

(defn- resolve-citations [content fetch-fn]
  (replace-mapped content cite-pattern
    (fn [[_ article-id-str as-of start-str end-str]]
      (try
        (let [article-id (Integer/parseInt article-id-str)
              start (Integer/parseInt start-str)
              end (Integer/parseInt end-str)
              article (fetch-fn article-id as-of)]
          (if article
            (let [text (or (:content article) "")
                  s (min start (count text))
                  e (min end (count text))
                  excerpt (subs text s e)]
              (str "\n\n> " (str/replace excerpt #"\n" "\n> ")
                   "\n>\n> — [" (:title article) "](/article/" article-id "/as-of/" as-of ")"
                   "\n\n"))
            (str "\n\n> [Citation not found: article " article-id " as-of " as-of "]\n\n")))
        (catch Exception _
          (str "\n\n> [Citation error]\n\n"))))))

(defn- render-footnotes-html [ref-ids def-map]
  (let [entries (keep-indexed
                  (fn [idx id]
                    (when-let [text (get def-map id)]
                      (let [html (or (markdown->html text) text)]
                        (str "<li id=\"fn-" (inc idx) "\">" html "</li>"))))
                  ref-ids)]
    (when (seq entries)
      (str "<section class=\"footnotes\"><h3>Footnotes</h3>\n<ol>\n"
           (str/join "\n" entries)
           "\n</ol></section>"))))

(def ^:private image-base-url (atom nil))

(defn set-image-base-url! [url]
  (reset! image-base-url url))

(defn- resolve-image-paths [html]
  (if-let [base @image-base-url]
    (str/replace html #"(src=\")(blog-images/)" (fn [[_ pre path]] (str pre base "/" path)))
    html))

;; Self-hosted video. A markdown image whose target is an .mp4 becomes a
;; <video>, so a clip drops into the prose exactly where a still would.
;;
;; Videos sit under blog-images/ beside the stills a post already has, rather
;; than in a blog-videos/ of their own. The name reads slightly off for an mp4,
;; and it buys three things: resolve-image-paths above needs no widening, a
;; post's media stays in one directory to back up and delete together, and the
;; edit page's file list shows an uploaded clip with no work.
;;
;; Same shape as embed-youtube below — a pass over the rendered HTML — because
;; flexmark runs with ESCAPE_HTML true, so a raw <video> tag written in a post is
;; escaped. That has to stay: comments and replies are markdown from the public,
;; so the tag must be generated here, never authored there.
(def ^:private video-src-pattern
  #"<img src=\"([^\"]+\.(?:mp4|webm|m4v))\"[^>]*>")

(def ^:private video-link-pattern
  #"<a href=\"([^\"]+\.(?:mp4|webm|m4v))\"[^>]*>[^<]*</a>")

(defn- video-tag [url]
  ;; preload="metadata" for the same reason music's audio player uses it: an
  ;; index page of posts should cost a few small requests, not a download each.
  (str "<video controls playsinline preload=\"metadata\""
       " style=\"display:block;width:100%;height:auto;margin:1.5rem 0;background:#000;\""
       " src=\"" url "\">"
       "<a href=\"" url "\">Download the video</a>"
       "</video>"))

(defn- embed-video [html]
  (-> html
      (str/replace video-src-pattern (fn [[_ url]] (video-tag url)))
      (str/replace video-link-pattern (fn [[_ url]] (video-tag url)))))

(def ^:private youtube-url-pattern
  #"(?:<a [^>]*href=\")?https?://(?:www\.)?(?:youtube\.com/watch\?v=|youtu\.be/)([a-zA-Z0-9_-]+)[^\"<\s]*(?:\"[^>]*>[^<]*</a>)?")

(defn- embed-youtube [html]
  (str/replace html youtube-url-pattern
    (fn [[_ video-id]]
      (str "<div style=\"position:relative;padding-bottom:56.25%;height:0;overflow:hidden;margin:1rem 0;\">"
           "<iframe src=\"https://www.youtube.com/embed/" video-id
           "\" style=\"position:absolute;top:0;left:0;width:100%;height:100%;border:0;\""
           " allowfullscreen></iframe></div>"))))

(def ^:private divider-pattern #"(?m)^-{3,}\s*$")

(defn- split-at-divider [content]
  (let [m (re-matcher divider-pattern content)]
    (if (.find m)
      [(subs content 0 (.start m)) (subs content (.end m))]
      [content nil])))

(defn- strip-divider [content]
  (replace-mapped content divider-pattern (constantly "")))

(defn render-content
  "The article page's rendering. With :positions?, Zen's view mode's: the same,
  and with data-pos on the blocks, counted in `content` as it was handed in."
  ([post fetch-fn] (render-content post fetch-fn {}))
  ([{:keys [content footnotes]} fetch-fn {:keys [positions?]}]
  (let [[content back] (strip-divider (or content ""))
        ref-ids (find-footnote-refs content)
        ref-order (into {} (map-indexed (fn [i id] [id i]) ref-ids))
        def-map (or (parse-footnote-defs footnotes) {})
        [processed back-cited] (resolve-citations content fetch-fn)
        html (if positions?
               (positions->text (or (render-markdown md-renderer-with-positions processed) "")
                                (comp back back-cited))
               (or (markdown->html processed) ""))
        html (replace-footnote-refs html ref-order def-map)
        html (resolve-image-paths html)
        html (embed-video html)
        html (embed-youtube html)
        footnotes-html (render-footnotes-html ref-ids def-map)]
    (str html (or footnotes-html "")))))

(defn render-content-preview [{:keys [content] :as post} fetch-fn]
  (let [[above below] (split-at-divider (or content ""))]
    (if below
      {:above-html (render-content (assoc post :content above) fetch-fn)
       :below-html (render-content (assoc post :content below) fetch-fn)
       :truncated? true}
      {:above-html (render-content post fetch-fn)
       :truncated? false})))

(defn render-article-content [{:keys [content]}]
  (or (markdown->html (or content "")) ""))
