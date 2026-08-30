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

(deftest the-target-directory-comes-from-the-clock-not-the-caller
  (let [prefix (images/today-prefix)]
    (is (re-matches #"blog-images/posts/\d{4}-\d{2}-\d{2}" prefix)
        "always today's post directory, and nothing else is expressible")))

(deftest an-unconfigured-uploader-refuses-loudly-rather-than-throwing
  (images/configure! nil)
  (is (false? (images/configured?)))
  (is (nil? (images/upload! (java.io.ByteArrayInputStream. (byte-array 3)) "a.png"))))

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
        token (t/login app)]
    (testing "no filename"
      (is (= 400 (:status (t/POST app "/post/1/image" {} token)))))
    (testing "not an image"
      (is (= 415 (:status (t/POST app "/post/1/image?filename=evil.php" {} token)))))
    (testing "an accepted name, but nothing configured to upload with"
      ;; 503 rather than a stack trace, and crucially it is reached only after
      ;; the extension check - so an unconfigured server still refuses .php.
      (is (= 503 (:status (t/POST app "/post/1/image?filename=ok.png" {} token)))))))
