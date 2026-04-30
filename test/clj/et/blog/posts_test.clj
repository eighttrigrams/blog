(ns et.blog.posts-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [hickory.select :as hs]
            [et.blog.test-support :as t]))

(deftest announcement-post-shows-latest-title-for-version
  (let [app (t/make-app)
        token (t/login app)]
    (t/create-and-publish! app token
      {"title" "Original Title" "content" "Body"} "Announcement")
    (Thread/sleep 1100)
    (t/POST app "/article/1"
      (t/article-params {"title" "Updated Title" "content" "Body"})
      token)
    (testing "posts list reflects the latest title for the announced version"
      (let [resp (t/GET app "/posts")]
        (is (str/includes? (:body resp) "Updated Title"))
        (is (not (str/includes? (:body resp) "Original Title")))))))

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

(deftest post-with-divider-shows-see-more
  (let [app (t/make-app)
        token (t/login app)
        _ (t/POST app "/posts"
            {"content" "Above the fold\n----\nBelow the fold" "footnotes" "" "image" ""}
            token)
        resp (t/GET app "/posts")
        html (t/parse resp)]
    (testing "posts list shows content above divider"
      (is (str/includes? (:body resp) "Above the fold")))
    (testing "posts list hides content below divider behind see-more"
      (let [details (t/select-all html (hs/class "post-see-more"))]
        (is (= 1 (count details)))
        (is (str/includes? (t/text-of (first details)) "Below the fold"))))
    (testing "individual post page renders full content with divider stripped"
      (let [resp (t/GET app "/post/1")
            body (:body resp)]
        (is (str/includes? body "Above the fold"))
        (is (str/includes? body "Below the fold"))
        (is (not (str/includes? body "See more")))
        (is (not (str/includes? body "<hr")))))))

(deftest post-without-divider-has-no-see-more
  (let [app (t/make-app)
        token (t/login app)
        _ (t/POST app "/posts"
            {"content" "Just a normal post" "footnotes" "" "image" ""}
            token)
        resp (t/GET app "/posts")
        html (t/parse resp)]
    (is (str/includes? (:body resp) "Just a normal post"))
    (is (empty? (t/select-all html (hs/class "post-see-more"))))))

(deftest standalone-post-shows-image
  (let [app (t/make-app)
        token (t/login app)
        _ (t/POST app "/posts"
            {"content" "Post with image" "footnotes" "" "image" "blog-images/test.jpg"}
            token)]
    (testing "posts list shows image"
      (let [resp (t/GET app "/posts")
            html (t/parse resp)
            imgs (t/select-all html (hs/class "article-preview"))]
        (is (= 1 (count imgs)))))
    (testing "individual post page shows image"
      (let [resp (t/GET app "/post/1")
            html (t/parse resp)
            imgs (t/select-all html (hs/class "article-preview"))]
        (is (= 1 (count imgs)))))))

(deftest delete-post
  (let [app (t/make-app)
        token (t/login app)
        resp (t/POST app "/posts" {"content" "Ephemeral post" "footnotes" "" "image" ""} token)
        post-id (str/replace (t/redirect-location resp) "/post/" "")]
    (is (= 200 (:status (t/GET app (str "/post/" post-id)))))
    (t/POST app (str "/post/" post-id "/delete") {} token)
    (is (= 404 (:status (t/GET app (str "/post/" post-id)))))
    (let [resp (t/GET app "/post/deleted" token)]
      (is (str/includes? (:body resp) "Ephemeral post")))))
