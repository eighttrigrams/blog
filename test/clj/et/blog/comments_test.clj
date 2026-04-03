(ns et.blog.comments-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [hickory.select :as hs]
            [et.blog.test-support :as t]))

(deftest submit-comment-on-article
  (let [app (t/make-app)
        token (t/login app)
        create-resp (t/POST app "/articles"
                      (t/article-params {"title" "Commentable" "content" "Discuss me"
                                         "publish" "1" "post-content" "Published post"})
                      token)
        article-id (str/replace (t/redirect-location create-resp) "/articles/" "")]
    (testing "comment form is accessible"
      (let [resp (t/GET app (str "/articles/" article-id "/version/1/comment"))]
        (is (= 200 (:status resp)))
        (is (str/includes? (:body resp) "Leave a comment"))))
    (testing "submitting a comment"
      (let [resp (t/POST app (str "/articles/" article-id "/version/1/comment")
                   {"email" "reader@example.com" "display-name" "Reader" "body" "Great article!"})]
        (is (= 302 (:status resp)))))
    (testing "comment appears on article page"
      (let [resp (t/GET app (str "/articles/" article-id "/version/1"))
            html (t/parse resp)]
        (is (= 200 (:status resp)))
        (is (some? (t/select-one html (hs/find-in-text #"Reader"))))
        (is (str/includes? (:body resp) "Great article!"))
        (is (not (str/includes? (:body resp) "reader@example.com"))
            "email must not be exposed on the page")))))

(deftest comment-with-missing-fields-rejected
  (let [app (t/make-app)
        token (t/login app)
        _ (t/POST app "/articles"
            (t/article-params {"title" "For Comment" "content" "Body"
                               "publish" "1" "post-content" "Post"})
            token)
        resp (t/POST app "/articles/1/version/1/comment"
               {"email" "" "display-name" "" "body" ""})]
    (is (= 400 (:status resp)))))
