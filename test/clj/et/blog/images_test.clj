(ns et.blog.images-test
  "The upload's guards. Nothing here touches the network: the uploader is left
  unconfigured, which is also one of the things under test, or where a happy path
  needs it, it talks to a fake FTP client that keeps its files in an atom."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.string :as str]
            [ring.mock.request :as mock]
            [et.blog.test-support :as t]
            [et.blog.images :as images]
            [taoensso.telemere :as tel])
  (:import [org.apache.commons.net.ftp FTPFile FTPSClient]))

(use-fixtures :each (fn [f] (images/configure! nil) (f) (images/configure! nil)))

(deftest a-filename-cannot-become-a-path
  ;; The remote path is built by joining this to a directory, so anything that
  ;; survives as a separator or a parent reference is a way out of the one place
  ;; this is allowed to write.
  (doseq [nasty ["../../etc/passwd.png"
                 "/absolute/evil.png"
                 "..\\windows\\evil.png"
                 "....//evil.png"
                 "sub/dir/pic.png"]]
    (testing nasty
      (let [safe (images/safe-name nasty)]
        (is (not (str/includes? safe "/")) "no forward slash survives")
        (is (not (str/includes? safe "\\")) "no backslash survives")
        (is (not (str/starts-with? safe ".")) "and it cannot start with a dot")
        (is (not (str/includes? safe "..")) "nor contain a parent reference")))))

(deftest a-blank-or-odd-name-still-produces-something-storable
  (is (= "image" (images/safe-name nil)))
  (is (= "image" (images/safe-name "")))
  (is (= "image" (images/safe-name "///")))
  (is (= "a-b.png" (images/safe-name "a  b.PNG")) "spaces collapse, case folds"))

(deftest a-name-keeps-what-it-can
  (is (= "arger.jpg" (images/safe-name "Ärger.jpg")) "the accent goes, not the letter")
  (is (= "v1.2.jpg" (images/safe-name "v1.2.jpg")) "dots inside a name stay")
  (is (= "lodz-aero-ss.jpg" (images/safe-name "Łódź Ærø ß.jpg")) "letters NFD does not take apart are spelled out")
  (testing "when nothing survives it is image.<ext>, and the original is logged"
    (let [signal (tel/with-signal (is (= "image.jpg" (images/safe-name "日本.jpg"))))]
      (is (= :warn (:level signal)))
      (is (str/includes? (force (:msg_ signal)) "日本.jpg"))))
  (is (nil? (tel/with-signal (images/safe-name "Ärger.jpg"))) "and nothing is logged when a name survives"))

(deftest only-real-image-extensions-are-accepted
  (doseq [ok ["a.jpg" "a.jpeg" "a.png" "a.gif" "a.webp" "a.avif" "A.PNG"]]
    (is (images/allowed-extension? ok) ok))
  (doseq [no ["a.svg" "a.php" "a.html" "a.js" "a" "a." "a.png.php" "noext"]]
    (is (not (images/allowed-extension? no)) no))
  (testing "svg is refused on purpose"
    ;; It is an image everywhere else and a script host here, served from
    ;; daniel-de-oliveira.com where it would run as that origin.
    (is (not (images/allowed-extension? "logo.svg")))))

(deftest the-target-directory-is-a-constant-prefix-plus-a-post-id
  (is (= "blog-images/posts/71" (images/post-prefix 71)))
  (testing "and it refuses anything that is not an integer"
    ;; The id reaches this from the URL, so if a string got through it would be
    ;; a way to name a directory. The handler parses it; this refuses to be the
    ;; second line of defence by accident.
    (doseq [bad ["71" "../evil" nil "71/../.." 7.5]]
      (is (thrown? clojure.lang.ExceptionInfo (images/post-prefix bad))
          (pr-str bad)))))

(deftest an-unconfigured-uploader-refuses-loudly-rather-than-throwing
  (images/configure! nil)
  (is (false? (images/configured?)))
  (is (nil? (images/upload! (java.io.ByteArrayInputStream. (byte-array 3)) "a.png" 71))))

(deftest a-partial-credential-counts-as-none
  (doseq [partial [{:host "h"} {:host "h" :username "u"} {:username "u" :password "p"} {}]]
    (images/configure! partial)
    (is (false? (images/configured?)) (pr-str partial))))

;; --- the endpoint -------------------------------------------------------

(deftest the-endpoint-is-owner-only
  (let [app (t/make-app)
        resp (t/POST app "/post/1/image?filename=a.png" {} nil)]
    (is (contains? #{302 401 403} (:status resp))
        "a visitor cannot write to the webspace")))

(deftest the-endpoint-refuses-what-it-should-before-it-connects
  (let [app (t/make-app)
        token (t/login app)
        ;; A real post, so the id in the path is one that exists.
        post-id (-> (t/POST app "/posts"
                            {"content" "Body" "footnotes" "" "image" ""}
                            token)
                    t/redirect-location
                    (str/replace "/post/" ""))
        at (fn [q] (:status (t/POST app (str "/post/" post-id "/image" q) {} token)))]
    (testing "not a post id"
      (is (= 400 (:status (t/POST app "/post/not-a-number/image?filename=a.png" {} token)))))
    (testing "a post that does not exist"
      (is (= 404 (:status (t/POST app "/post/99999/image?filename=a.png" {} token)))))
    (testing "no filename"
      (is (= 400 (at ""))))
    (testing "not an image"
      (is (= 415 (at "?filename=evil.php"))))
    (testing "an accepted name, but nothing configured to upload with"
      ;; 503 rather than a stack trace, and crucially it is reached only after
      ;; the extension check - so an unconfigured server still refuses .php.
      (is (= 503 (at "?filename=ok.png"))))))

;; --- the listing --------------------------------------------------------

(deftest listing-is-scoped-the-same-way-uploading-is
  (testing "an unconfigured server lists nothing rather than throwing"
    (images/configure! nil)
    (is (nil? (images/list-post-files 71))))
  (testing "and it refuses a post id that is not an integer"
    (images/configure! {:host "h" :username "u" :password "p"})
    (doseq [bad ["71" "../evil" nil]]
      (is (thrown? clojure.lang.ExceptionInfo (images/post-prefix bad))
          (pr-str bad)))))

(deftest the-listing-endpoint-is-owner-only-and-validates-the-id
  (let [app (t/make-app)
        token (t/login app)]
    (is (contains? #{302 401 403} (:status (t/GET app "/post/1/images" nil)))
        "a visitor cannot enumerate the webspace")
    (is (= 400 (:status (t/GET app "/post/not-a-number/images" token))))
    (testing "and with nothing configured it answers an empty list, not an error"
      ;; Every dev laptop is in this state, and an editor that shouts about it
      ;; on every page load would be worse than one that says nothing.
      (let [resp (t/GET app "/post/1/images" token)]
        (is (= 200 (:status resp)))
        (is (str/includes? (:body resp) "\"unconfigured\":true"))))))

;; --- an article's pictures, dropped or pasted into its content ----------

(deftest an-articles-folder-is-a-constant-prefix-plus-its-id
  ;; Where article pictures have always lived, and nothing a caller can steer.
  (is (= "blog-images/41" (images/article-prefix 41)))
  (doseq [bad ["41" "../evil" nil "41/../.." 7.5]]
    (is (thrown? clojure.lang.ExceptionInfo (images/article-prefix bad))
        (pr-str bad))))

(deftest a-taken-name-gets-a-number
  (is (= "a.jpg" (images/free-name "a.jpg" [])))
  (is (= "a-2.jpg" (images/free-name "a.jpg" ["a.jpg"])))
  (is (= "a-3.jpg" (images/free-name "a.jpg" ["a.jpg" "a-2.jpg" "b.jpg"])))
  (is (= "image-2" (images/free-name "image" ["image"])))
  (testing "a folder that could not be read: a name that cannot exist yet"
    (is (re-matches #"a-\d{8}-\d{6}\.jpg" (images/free-name "a.jpg" nil)))))

(defn- create-article! [app token]
  (->> (t/POST app "/article" (t/article-params {"title" "Pictures"}) token)
       t/redirect-location
       (re-find #"^/article/(\d+)")
       second))

(defn- POST-bytes [app path ^bytes bytes token]
  (app (-> (mock/request :post path)
           (mock/body bytes)
           (mock/header "cookie" (str "token=" token)))))

(deftest the-article-endpoint-is-owner-only
  (let [app (t/make-app)
        resp (t/POST app "/article/1/image?filename=a.png" {} nil)]
    (is (contains? #{302 401 403} (:status resp))
        "a visitor cannot write to the webspace")))

(deftest the-article-endpoint-refuses-what-it-should-before-it-connects
  (let [app (t/make-app)
        token (t/login app)
        id (create-article! app token)
        at (fn [q] (:status (t/POST app (str "/article/" id "/image" q) {} token)))]
    (testing "not an article id"
      (is (= 400 (:status (t/POST app "/article/not-a-number/image?filename=a.png" {} token)))))
    (testing "an article that does not exist"
      (is (= 404 (:status (t/POST app "/article/99999/image?filename=a.png" {} token)))))
    (testing "no filename"
      (is (= 400 (at ""))))
    (testing "not a picture, and no SVG"
      (is (= 415 (at "?filename=notes.pdf")))
      (is (= 415 (at "?filename=drawing.svg"))))
    (testing "a picture, but nothing configured to upload with, as in development"
      (let [resp (t/POST app (str "/article/" id "/image?filename=ok.png") {} token)]
        (is (= 503 (:status resp)))
        (is (= "Image upload is not configured on this server."
               (:error (t/json-body resp))))))))

(defn- fake-ftp
  "An FTPS client that keeps its files in `files`, an atom of path -> bytes, and
  never opens a socket. It answers with reply codes the way a server does.
  A directory exists when a file is in it or it was made (MKD) into `:dirs`, an
  atom of paths, and a file can only be stored into one that exists.
  Options: `:listing` a reply code to refuse every listing with; `:mlst` a reply
  code MLST answers with whatever is there (SIZE always tells the truth);
  `:store-fails?` leaves a three-byte stub and refuses, as a transfer that dies
  part-way does; `:stall`, an atom of a count, makes that many uploads' writes
  block until their data stream is closed, as a server that stops reading does."
  [files {:keys [listing mlst store-fails? dirs stall] :or {dirs (atom #{})}}]
  (let [code (atom 200)
        answer! (fn [c] (reset! code c))
        dir? (fn [d] (or (contains? @dirs d)
                         (some #(str/starts-with? % (str d "/")) (keys @files))))
        in-dir (fn [dir]
                 (if listing
                   (do (answer! listing) (into-array FTPFile []))
                   (do (answer! 226)
                       (into-array FTPFile
                                   (for [p (keys @files)
                                         :when (str/starts-with? p (str dir "/"))]
                                     (doto (FTPFile.) (.setName (subs p (inc (count dir))))))))))]
    (proxy [FTPSClient] []
      (getReplyCode [] @code)
      (getReplyString [] (str @code " fake"))
      (changeWorkingDirectory [d] (boolean (dir? d)))
      (makeDirectory [d] (swap! dirs conj d) true)
      (mlistDir [dir] (in-dir dir))
      (listFiles [dir] (in-dir dir))
      (sendCommand [cmd path]
        (answer! (if (and (= "MLST" cmd) mlst) mlst (if (contains? @files path) 250 550))))
      (storeFileStream [path]
        (cond
          (not (dir? (subs path 0 (str/last-index-of path "/"))))
          (do (answer! 553) nil)

          (and stall (pos? (first (swap-vals! stall #(max 0 (dec %))))))
          (let [closed (promise)
                block #(do @closed (throw (java.io.IOException. "Socket closed")))]
            ;; A real SocketOutputStream, as storeFileStream answers, over a
            ;; socket whose close is the only thing that ends the write.
            (org.apache.commons.net.io.SocketOutputStream.
              (proxy [java.net.Socket] []
                (setSoLinger [_ _])
                (close [] (deliver closed true)))
              (proxy [java.io.OutputStream] []
                (write ([_] (block)) ([_ _ _] (block))))))

          :else
          (let [buf (java.io.ByteArrayOutputStream.)]
            (proxy [java.io.FilterOutputStream] [buf]
              (close [] (swap! files assoc path (if store-fails? (byte-array 3) (.toByteArray buf))))))))
      (completePendingCommand [] (answer! (if store-fails? 451 226)) (not store-fails?))
      (deleteFile [path] (swap! files dissoc path) true)
      (logout [] true)
      (disconnect [] nil))))

(defn- with-fake-ftp [files opts f]
  (images/configure! {:host "h" :username "u" :password "p"})
  (with-redefs-fn {#'images/open (fn [_] (fake-ftp files opts))} f))

(defn- text [files path] (some-> (get @files path) (String.)))

(deftest a-picture-goes-into-its-articles-folder-and-overwrites-nothing
  (let [app (t/make-app)
        token (t/login app)
        id (create-article! app token)
        files (atom {(str "/blog-images/" id "/cinching-corset.jpg") (.getBytes "the first")})
        drop! (fn [content]
                (POST-bytes app (str "/article/" id "/image?filename=Cinching%20Corset.jpg")
                            (.getBytes ^String content) token))]
    (with-fake-ftp files {}
      (fn []
        (let [second-drop (drop! "the second")
              third-drop (drop! "the third")]
          (is (= 200 (:status second-drop)))
          (is (= (str "blog-images/" id "/cinching-corset-2.jpg")
                 (:path (t/json-body second-drop))))
          (is (= (str "blog-images/" id "/cinching-corset-3.jpg")
                 (:path (t/json-body third-drop))))
          (is (= "the first" (text files (str "/blog-images/" id "/cinching-corset.jpg")))
              "the picture that was there is untouched"))))))

(deftest a-folder-that-cannot-be-read-is-not-taken-for-an-empty-one
  ;; A refused listing used to read as "nothing there", and the upload went onto
  ;; the existing picture. Now the name is one that cannot exist yet.
  (let [app (t/make-app)
        token (t/login app)
        id (create-article! app token)
        existing (str "/blog-images/" id "/cinching-corset.jpg")
        files (atom {existing (.getBytes "the first")})]
    (with-fake-ftp files {:listing 550}
      (fn []
        (let [resp (POST-bytes app (str "/article/" id "/image?filename=Cinching%20Corset.jpg")
                               (.getBytes "the second") token)]
          (is (= 200 (:status resp)))
          (is (re-matches (re-pattern (str "blog-images/" id "/cinching-corset-\\d{8}-\\d{6}\\.jpg"))
                          (:path (t/json-body resp))))
          (is (= "the first" (text files existing))))))))

(deftest nothing-is-written-unless-the-server-says-that-name-is-free
  (let [app (t/make-app)
        token (t/login app)
        id (create-article! app token)
        drop! #(POST-bytes app (str "/article/" id "/image?filename=a.jpg") (.getBytes "new") token)]
    (testing "MLST says something is there, whatever the listing said: refused in words"
      (let [files (atom {})]
        (with-fake-ftp files {:mlst 250}
          (fn []
            (let [resp (drop!)]
              (is (= 502 (:status resp)))
              (is (str/includes? (:error (t/json-body resp)) "would not confirm"))
              (is (empty? @files) "nothing was written"))))))
    (testing "a server that does not know MLST is asked SIZE instead"
      (let [files (atom {})]
        (with-fake-ftp files {:mlst 502}
          (fn []
            (is (= 200 (:status (drop!))))
            (is (= "new" (text files (str "/blog-images/" id "/a.jpg"))))))))))

(deftest a-half-written-file-is-removed-only-when-its-name-was-new
  (let [app (t/make-app)
        token (t/login app)
        article-id (create-article! app token)
        post-id (-> (t/POST app "/posts" {"content" "Body" "footnotes" "" "image" ""} token)
                    t/redirect-location
                    (str/replace "/post/" ""))]
    (testing "an article picture under a new name: the stub goes"
      (let [files (atom {})]
        (with-fake-ftp files {:store-fails? true}
          (fn []
            (let [resp (POST-bytes app (str "/article/" article-id "/image?filename=a.jpg")
                                   (.getBytes "new") token)]
              (is (= 502 (:status resp)))
              (is (= "Upload failed: FTP refused the upload - 451 fake" (:error (t/json-body resp)))
                  "its own reason, not called a cut: the deadline never fired"))
            (is (empty? @files))))))
    (testing "a post picture written over an existing name: what is there stays"
      (let [path (str "/blog-images/posts/" post-id "/a.jpg")
            files (atom {path (.getBytes "the first")})]
        (with-fake-ftp files {:store-fails? true}
          (fn []
            (is (= 502 (:status (POST-bytes app (str "/post/" post-id "/image?filename=a.jpg")
                                            (.getBytes "new") token))))
            (is (contains? @files path) "not deleted: it may be a picture that was there")))))))

(deftest a-post-picture-still-lands-where-it-always-did
  ;; Through the store! the article upload shares, and still over the same name.
  (let [app (t/make-app)
        token (t/login app)
        post-id (-> (t/POST app "/posts" {"content" "Body" "footnotes" "" "image" ""} token)
                    t/redirect-location
                    (str/replace "/post/" ""))
        files (atom {})
        up! #(POST-bytes app (str "/post/" post-id "/image?filename=Pic.png") (.getBytes ^String %) token)]
    (with-fake-ftp files {}
      (fn []
        (is (= (str "blog-images/posts/" post-id "/pic.png") (:path (t/json-body (up! "one")))))
        (is (= (str "blog-images/posts/" post-id "/pic.png") (:path (t/json-body (up! "two")))))
        (is (= "two" (text files (str "/blog-images/posts/" post-id "/pic.png"))))))))

(deftest the-article-endpoint-refuses-an-empty-or-oversized-body
  (let [app (t/make-app)
        token (t/login app)
        id (create-article! app token)
        files (atom {})]
    (with-fake-ftp files {}
      (fn []
        (let [empty-resp (POST-bytes app (str "/article/" id "/image?filename=a.jpg") (byte-array 0) token)
              big-resp (POST-bytes app (str "/article/" id "/image?filename=a.jpg")
                                   (byte-array (inc images/max-bytes)) token)]
          (is (= 400 (:status empty-resp)))
          (is (= "The file was empty." (:error (t/json-body empty-resp))))
          (is (= 413 (:status big-resp)))
          (is (empty? @files)))))))

(deftest a-new-articles-folder-is-made-for-its-first-picture
  (let [app (t/make-app)
        token (t/login app)
        id (create-article! app token)
        files (atom {})
        dirs (atom #{"/blog-images"})]
    (with-fake-ftp files {:dirs dirs}
      (fn []
        (is (= (str "blog-images/" id "/a.jpg")
               (:path (t/json-body (POST-bytes app (str "/article/" id "/image?filename=a.jpg")
                                               (.getBytes "new") token)))))
        (is (contains? @dirs (str "/blog-images/" id)) "MKD for the article's own folder")
        (is (= "new" (text files (str "/blog-images/" id "/a.jpg"))))))))

(deftest a-stalled-upload-is-cut-off-and-the-next-one-gets-the-lock
  ;; A server that stops reading the data channel blocks a write for good: Java
  ;; has no write timeout. The session's deadline closes it, and article-lock is
  ;; free for the upload queued behind it. (session-ms is 90 s; 0.3 s here.)
  (let [app (t/make-app)
        token (t/login app)
        id (create-article! app token)
        files (atom {})
        up! #(future (let [t (System/currentTimeMillis)
                           r (POST-bytes app (str "/article/" id "/image?filename=" %)
                                         (.getBytes "picture") token)]
                       (assoc r :ms (- (System/currentTimeMillis) t))))]
    (with-redefs-fn {#'images/session-ms 300}
      #(with-fake-ftp files {:stall (atom 1)}
         (fn []
           (let [stalled (up! "a.jpg")
                 _ (Thread/sleep 100)
                 queued (up! "b.jpg")
                 stalled @stalled
                 queued @queued]
             (is (= 502 (:status stalled)))
             (is (str/includes? (:error (t/json-body stalled)) "cut off - Socket closed")
                 "the cut, and what it broke")
             (is (< (:ms stalled) 3000) "ended by the deadline, not by a hang")
             (is (= 200 (:status queued)) "the next upload got the lock")
             (is (= "picture" (text files (str "/blog-images/" id "/b.jpg"))))))))))

(deftest a-cut-keeps-the-real-reason-for-the-log
  ;; The handler logs .getMessage and ex-data. "cut off {}" said nothing about
  ;; what the deadline broke.
  (let [e (#'images/cut-off (ex-info "FTP refused the upload - 426 aborted"
                                     {:path "/blog-images/41/a.jpg" :reply "426 aborted"}))]
    (is (str/includes? (.getMessage e) "cut off - FTP refused the upload - 426 aborted"))
    (is (= {:path "/blog-images/41/a.jpg" :reply "426 aborted" :deadline-ms 90000} (ex-data e))))
  (is (str/includes? (.getMessage (#'images/cut-off (java.net.SocketTimeoutException. "Read timed out")))
                     "cut off - Read timed out")))
