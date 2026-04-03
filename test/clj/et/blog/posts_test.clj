(ns et.blog.posts-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [hickory.select :as hs]
            [et.blog.test-support :as t]))

(deftest create-and-view-post
  (let [app (t/make-app)
        token (t/login app)
        resp (t/POST app "/posts"
               {"content" "Hello from a post" "footnotes" "" "image" ""}
               token)
        post-path (t/redirect-location resp)]
    (is (= 302 (:status resp)))
    (testing "post page renders content"
      (let [resp (t/GET app post-path)]
        (is (= 200 (:status resp)))
        (is (str/includes? (:body resp) "Hello from a post"))))
    (testing "post appears in posts list"
      (let [resp (t/GET app "/posts")
            html (t/parse resp)
            lis (t/select-all html (hs/descendant (hs/class "post-list") (hs/tag :li)))]
        (is (= 1 (count lis)))
        (is (str/includes? (:body resp) "Hello from a post"))))))

(deftest delete-post
  (let [app (t/make-app)
        token (t/login app)
        resp (t/POST app "/posts" {"content" "Ephemeral post" "footnotes" "" "image" ""} token)
        post-id (str/replace (t/redirect-location resp) "/posts/" "")]
    (is (= 200 (:status (t/GET app (str "/posts/" post-id)))))
    (t/POST app (str "/posts/" post-id "/delete") {} token)
    (is (= 404 (:status (t/GET app (str "/posts/" post-id)))))
    (let [resp (t/GET app "/posts/deleted" token)]
      (is (str/includes? (:body resp) "Ephemeral post")))))
