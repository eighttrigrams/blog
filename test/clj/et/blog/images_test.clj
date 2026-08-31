(ns et.blog.images-test
  "The upload's guards. Nothing here touches the network: the uploader is left
  unconfigured, which is also one of the things under test."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.string :as str]
            [et.blog.test-support :as t]
            [et.blog.images :as images]))

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
