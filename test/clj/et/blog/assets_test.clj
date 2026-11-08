(ns et.blog.assets-test
  "Cache busting: blog's own scripts and stylesheets are linked with a hash of
  their content (views/asset), so a changed file is a new URL and a browser never
  runs yesterday's file with today's page."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hickory.select :as hs]
            [et.blog.views :as views]
            [et.blog.test-support :as t])
  (:import [java.security MessageDigest]))

(defn- sha-256-prefix [bytes]
  (subs (format "%064x" (BigInteger. 1 (.digest (MessageDigest/getInstance "SHA-256") bytes))) 0 10))

(defn- slurp-bytes [url]
  (with-open [in (io/input-stream url) out (java.io.ByteArrayOutputStream.)]
    (io/copy in out)
    (.toByteArray out)))

(deftest every-local-script-and-stylesheet-is-linked-with-its-content-hash
  (let [app (t/make-app)
        token (t/login app)]
    (t/POST app "/article" (t/article-params {"title" "T" "content" "x"}) token)
    (let [html (t/parse (t/GET app "/article/1/edit" token))
          links (concat (keep #(get-in % [:attrs :src]) (t/select-all html (hs/tag :script)))
                        (keep #(get-in % [:attrs :href])
                              (t/select-all html (hs/and (hs/tag :link) (hs/attr :rel #(= % "stylesheet"))))))
          local (filter #(str/starts-with? % "/") links)]
      (is (<= 7 (count local)) "hljs (3), the editor bundle, editors.js, article-images.js, zen.js")
      (doseq [link local]
        (let [[_ path v] (re-matches #"(/[^?]+)\?v=([0-9a-f]{10})" link)]
          (is (some? v) (str link " carries ?v=<hash>"))
          (when v
            (is (= (sha-256-prefix (slurp-bytes (io/resource (str "public/blog" path)))) v)
                (str path ": the hash is of what the file holds")))))
      (is (some #(str/starts-with? % "https://fonts.googleapis.com/") links))
      (is (not-any? #(and (str/starts-with? % "https://") (str/includes? % "?v=")) links)
          "nobody else's URL is touched"))))

(deftest a-changed-file-is-a-new-link
  (let [file (doto (java.io.File/createTempFile "zen" ".js") (.deleteOnExit))
        url (.toURL file)
        link #(with-redefs [io/resource (fn [p] (when (= p "public/blog/js/x.js") url))]
                (views/asset "/js/x.js"))]
    (spit file "var a=1;")
    (let [before (link)]
      (is (= before (link)) "the same file, the same link")
      (spit file "var a=2;")
      (.setLastModified file (+ (.lastModified file) 2000))
      (is (not= before (link)) "changed under a running server, a new link")))
  (is (= "/js/not-there.js" (views/asset "/js/not-there.js"))
      "a file that is not there is linked as it was"))

(deftest a-versioned-file-is-kept-a-year-an-unversioned-one-is-not
  (let [app (t/make-app)
        versioned (t/GET app (views/asset "/js/zen.js"))
        plain (t/GET app "/js/zen.js")]
    (is (= 200 (:status versioned)))
    (is (= "public, max-age=31536000, immutable" (get-in versioned [:headers "Cache-Control"])))
    (is (= 200 (:status plain)))
    (is (nil? (get-in plain [:headers "Cache-Control"])))))
