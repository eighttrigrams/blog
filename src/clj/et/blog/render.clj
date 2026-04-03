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

(defn- externalize-links [html]
  (str/replace html #"<a href=\"(https?://[^\"]+)\""
    (fn [[_ url]]
      (str "<a href=\"" url "\" target=\"_blank\" rel=\"noopener\""))))

(defn markdown->html [text]
  (when (and text (not (str/blank? text)))
    (let [doc (.parse md-parser text)]
      (externalize-links (.render md-renderer doc)))))

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
  (str/replace content cite-pattern
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

(def ^:private youtube-url-pattern
  #"(?:<a [^>]*href=\")?https?://(?:www\.)?(?:youtube\.com/watch\?v=|youtu\.be/)([a-zA-Z0-9_-]+)[^\"<\s]*(?:\"[^>]*>[^<]*</a>)?")

(defn- embed-youtube [html]
  (str/replace html youtube-url-pattern
    (fn [[_ video-id]]
      (str "<div style=\"position:relative;padding-bottom:56.25%;height:0;overflow:hidden;margin:1rem 0;\">"
           "<iframe src=\"https://www.youtube.com/embed/" video-id
           "\" style=\"position:absolute;top:0;left:0;width:100%;height:100%;border:0;\""
           " allowfullscreen></iframe></div>"))))

(defn render-content [{:keys [content footnotes]} fetch-fn]
  (let [content (or content "")
        ref-ids (find-footnote-refs content)
        ref-order (into {} (map-indexed (fn [i id] [id i]) ref-ids))
        def-map (or (parse-footnote-defs footnotes) {})
        processed (resolve-citations content fetch-fn)
        html (or (markdown->html processed) "")
        html (replace-footnote-refs html ref-order def-map)
        html (resolve-image-paths html)
        html (embed-youtube html)
        footnotes-html (render-footnotes-html ref-ids def-map)]
    (str html (or footnotes-html ""))))

(defn render-article-content [{:keys [content]}]
  (or (markdown->html (or content "")) ""))
