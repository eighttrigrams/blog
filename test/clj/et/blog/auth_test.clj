(ns et.blog.auth-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [et.blog.test-support :as t]))

(deftest login-flow
  (let [app (t/make-app)]
    (testing "wrong password returns 401"
      (let [resp (t/POST app "/login" {"password" "wrong"})]
        (is (= 401 (:status resp)))
        (is (str/includes? (:body resp) "Wrong password"))))
    (testing "correct password redirects with token cookie"
      (let [resp (t/POST app "/login" {"password" "admin"})]
        (is (= 302 (:status resp)))
        (is (= "/" (t/redirect-location resp)))
        (is (str/includes? (or (get-in resp [:headers "Set-Cookie"]) "") "token="))))))

(deftest unauthenticated-access-redirects-to-login
  (let [app (t/make-app)]
    (doseq [path ["/article/drafts" "/article/new"]]
      (let [resp (t/GET app path)]
        (is (= 302 (:status resp)))
        (is (= "/login" (t/redirect-location resp))
            (str "GET " path " should redirect when unauthenticated"))))
    (let [resp (t/POST app "/article" {"title" "Sneaky" "content" "Nope"})]
      (is (= 302 (:status resp)))
      (is (= "/login" (t/redirect-location resp))))))
