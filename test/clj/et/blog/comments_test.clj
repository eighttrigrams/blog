(ns et.blog.comments-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [hickory.select :as hs]
            [et.blog.test-support :as t]))

(deftest submit-comment-on-article
  (let [app (t/make-app)
        token (t/login app)
        create-resp (t/POST app "/article"
                      (t/article-params {"title" "Commentable" "content" "Discuss me"
                                         "publish" "1" "post-content" "Published post"})
                      token)
        article-id (str/replace (t/redirect-location create-resp) "/article/" "")]
    (testing "comment form is accessible"
      (let [resp (t/GET app (str "/article/" article-id "/version/1/comment"))]
        (is (= 200 (:status resp)))
        (is (str/includes? (:body resp) "Leave a comment"))))
    (testing "submitting a comment"
      (let [resp (t/POST app (str "/article/" article-id "/version/1/comment")
                   {"email" "reader@example.com" "display-name" "Reader" "body" "Great article!"})]
        (is (= 302 (:status resp)))))
    (testing "comment appears on article page"
      (let [resp (t/GET app (str "/article/" article-id "/version/1"))
            html (t/parse resp)]
        (is (= 200 (:status resp)))
        (is (some? (t/select-one html (hs/find-in-text #"Reader"))))
        (is (str/includes? (:body resp) "Great article!"))
        (is (not (str/includes? (:body resp) "reader@example.com"))
            "email must not be exposed on the page")))))

(deftest comment-with-missing-fields-rejected
  (let [app (t/make-app)
        token (t/login app)
        _ (t/POST app "/article"
            (t/article-params {"title" "For Comment" "content" "Body"
                               "publish" "1" "post-content" "Post"})
            token)
        resp (t/POST app "/article/1/version/1/comment"
               {"email" "" "display-name" "" "body" ""})]
    (is (= 400 (:status resp)))))

(deftest comment-has-own-page
  (let [app (t/make-app)
        token (t/login app)
        _ (t/POST app "/article"
            (t/article-params {"title" "My Article" "content" "Content"
                               "publish" "1" "post-content" "Post"})
            token)
        _ (t/POST app "/article/1/version/1/comment"
            {"email" "a@b.com" "display-name" "Alice" "body" "Nice work!"})]
    (testing "comment page renders with # before title"
      (let [resp (t/GET app "/article/1/version/1/comment/1")]
        (is (= 200 (:status resp)))
        (is (str/includes? (:body resp) "# My Article"))
        (is (str/includes? (:body resp) "Alice"))
        (is (str/includes? (:body resp) "Nice work!"))))
    (testing "wrong version returns 404"
      (let [resp (t/GET app "/article/1/version/2/comment/1")]
        (is (= 404 (:status resp)))))
    (testing "nonexistent comment returns 404"
      (let [resp (t/GET app "/article/1/version/1/comment/999")]
        (is (= 404 (:status resp)))))))

(deftest article-comments-list
  (let [app (t/make-app)
        token (t/login app)
        _ (t/POST app "/article"
            (t/article-params {"title" "Versioned" "content" "V1"
                               "publish" "1" "post-content" "Post v1"})
            token)
        _ (Thread/sleep 1100)
        _ (t/POST app "/article/1"
            (t/article-params {"title" "Versioned" "content" "V2"
                               "publish" "1" "post-content" "Post v2"})
            token)
        _ (t/POST app "/article/1/version/1/comment"
            {"email" "a@b.com" "display-name" "Alice" "body" "Comment on v1"})
        _ (t/POST app "/article/1/version/2/comment"
            {"email" "b@c.com" "display-name" "Bob" "body" "Comment on v2"})]
    (testing "all comments page shows both comments"
      (let [resp (t/GET app "/article/1/comments")]
        (is (= 200 (:status resp)))
        (is (str/includes? (:body resp) "Alice"))
        (is (str/includes? (:body resp) "Bob"))
        (is (str/includes? (:body resp) "Comment on v1"))
        (is (str/includes? (:body resp) "Comment on v2"))))
    (testing "version-specific comments page shows only that version"
      (let [resp (t/GET app "/article/1/version/1/comments")]
        (is (= 200 (:status resp)))
        (is (str/includes? (:body resp) "Alice"))
        (is (not (str/includes? (:body resp) "Bob"))))
      (let [resp (t/GET app "/article/1/version/2/comments")]
        (is (= 200 (:status resp)))
        (is (str/includes? (:body resp) "Bob"))
        (is (not (str/includes? (:body resp) "Alice")))))))

(deftest comment-on-older-version
  (let [app (t/make-app)
        token (t/login app)
        _ (t/POST app "/article"
            (t/article-params {"title" "Multi" "content" "V1"
                               "publish" "1" "post-content" "Post"})
            token)
        _ (Thread/sleep 1100)
        _ (t/POST app "/article/1"
            (t/article-params {"title" "Multi" "content" "V2"
                               "publish" "1" "post-content" "Post v2"})
            token)]
    (testing "can comment on older version"
      (let [resp (t/POST app "/article/1/version/1/comment"
                   {"email" "a@b.com" "display-name" "Old" "body" "On v1"})]
        (is (= 302 (:status resp)))
        (is (= "/article/1/version/1" (t/redirect-location resp)))))
    (testing "comment form is accessible for older version"
      (let [resp (t/GET app "/article/1/version/1/comment")]
        (is (= 200 (:status resp)))))))
