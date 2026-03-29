(ns et.blog.render
  (:require [clojure.string :as str])
  (:import [com.vladsch.flexmark.html HtmlRenderer]
           [com.vladsch.flexmark.parser Parser]
           [com.vladsch.flexmark.util.data MutableDataSet]))

(def ^:private md-options (doto (MutableDataSet.)
                            (.set HtmlRenderer/SOFT_BREAK "<br />\n")))
(def ^:private md-parser (.build (Parser/builder md-options)))
(def ^:private md-renderer (.build (HtmlRenderer/builder md-options)))

(defn markdown->html [text]
  (when (and text (not (str/blank? text)))
    (let [doc (.parse md-parser text)]
      (.render md-renderer doc))))

(def ^:private footnote-ref-pattern #"FOOTNOTE:([a-zA-Z0-9_-]+)")
(def ^:private cite-pattern #"CITE:(\d+):(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}):(\d+):(\d+)")

(defn- find-footnote-refs [content]
  (let [matches (re-seq footnote-ref-pattern content)]
    (vec (distinct (map second matches)))))

(defn- parse-footnote-defs [footnotes-text]
  (when (and footnotes-text (not (str/blank? footnotes-text)))
    (->> (str/split-lines footnotes-text)
         (keep (fn [line]
                 (let [trimmed (str/trim line)]
                   (when-let [[_ id text] (re-matches #"FOOTNOTE:([a-zA-Z0-9_-]+)\s+(.*)" trimmed)]
                     [id text]))))
         (into {}))))

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
                   "\n>\n> — [" (:title article) "](/articles/" article-id "/as-of/" as-of ")"
                   "\n\n"))
            (str "\n\n> [Citation not found: article " article-id " as-of " as-of "]\n\n")))
        (catch Exception _
          (str "\n\n> [Citation error]\n\n"))))))

(defn- render-footnotes-html [ref-ids def-map]
  (let [entries (keep-indexed
                  (fn [idx id]
                    (when-let [text (get def-map id)]
                      (str "<li id=\"fn-" (inc idx) "\">" text "</li>")))
                  ref-ids)]
    (when (seq entries)
      (str "<section class=\"footnotes\"><h3>Footnotes</h3>\n<ol>\n"
           (str/join "\n" entries)
           "\n</ol></section>"))))

(defn render-content [{:keys [content footnotes]} fetch-fn]
  (let [content (or content "")
        ref-ids (find-footnote-refs content)
        ref-order (into {} (map-indexed (fn [i id] [id i]) ref-ids))
        def-map (or (parse-footnote-defs footnotes) {})
        processed (-> content
                      (resolve-citations fetch-fn)
                      (replace-footnote-refs ref-order def-map))
        html (markdown->html processed)
        footnotes-html (render-footnotes-html ref-ids def-map)]
    (str (or html "") (or footnotes-html ""))))
