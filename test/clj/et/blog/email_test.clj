(ns et.blog.email-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [et.blog.test-support :as t]))

(deftest email-subscription
  (let [app (t/make-app)
        token (t/login app)]
    (testing "subscribe"
      (let [resp (t/POST app "/email" {"email" "sub@example.com" "action" "subscribe"})]
        (is (= 200 (:status resp)))
        (is (str/includes? (:body resp) "Thanks for subscribing!"))))
    (testing "subscriber visible to admin"
      (is (str/includes? (:body (t/GET app "/email" token)) "sub@example.com")))
    (testing "unsubscribe"
      (t/POST app "/email" {"email" "sub@example.com" "action" "unsubscribe"})
      (is (not (str/includes? (:body (t/GET app "/email" token)) "sub@example.com"))))))
