(ns et.blog.articles-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [hickory.select :as hs]
            [et.blog.test-support :as t]))

(deftest home-page-starts-empty
  (let [app (t/make-app)
        resp (t/GET app "/")
        html (t/parse resp)]
    (is (= 200 (:status resp)))
    (is (some? (t/select-one html (hs/find-in-text #"No articles yet\.")))
        "empty DB must show placeholder text")
    (is (empty? (t/select-all html (hs/class "article-list")))
        "no article-list element when empty")))

(deftest create-draft-article
  (let [app (t/make-app)
        token (t/login app)
        resp (t/POST app "/articles"
               (t/article-params {"title" "Draft Article" "content" "Draft body"})
               token)]
    (is (= 302 (:status resp)))
    (is (str/starts-with? (t/redirect-location resp) "/article/"))
    (testing "draft does NOT appear on public home page"
      (let [home (t/GET app "/")]
        (is (str/includes? (:body home) "No articles yet."))
        (is (not (str/includes? (:body home) "Draft Article")))))
    (testing "draft appears on authenticated drafts page"
      (let [drafts (t/GET app "/article/drafts" token)
            html (t/parse drafts)
            h2s (t/select-all html (hs/tag :h2))]
        (is (= 200 (:status drafts)))
        (is (= 1 (count (filter #(str/includes? (t/text-of %) "Draft Article") h2s))))))))

(deftest create-published-article
  (let [app (t/make-app)
        token (t/login app)
        resp (t/POST app "/articles"
               (t/article-params {"title" "Published Article" "content" "Article body here"
                                  "subtitle" "A subtitle"
                                  "publish" "1" "post-content" "Post announcement text"})
               token)
        article-id (str/replace (t/redirect-location resp) "/article/" "")]
    (is (= 302 (:status resp)))
    (testing "published article appears on public home page"
      (let [home (t/GET app "/")
            html (t/parse home)
            lis (t/select-all html (hs/descendant (hs/class "article-list") (hs/tag :li)))]
        (is (not (str/includes? (:body home) "No articles yet.")))
        (is (str/includes? (:body home) "Published Article"))
        (is (str/includes? (:body home) "A subtitle"))
        (is (= 1 (count lis)) "exactly one article in the list")))
    (testing "article page renders title, content, and version badge"
      (let [resp (t/GET app (str "/article/" article-id))
            html (t/parse resp)]
        (is (= 200 (:status resp)))
        (is (= "Published Article" (t/text-of (t/select-one html (hs/tag :h1)))))
        (is (str/includes? (:body resp) "Article body here"))
        (is (str/includes? (:body resp) "v1"))))
    (testing "associated post appears on posts page"
      (let [resp (t/GET app "/posts")]
        (is (str/includes? (:body resp) "Post announcement text"))
        (is (not (str/includes? (:body resp) "No posts yet.")))))))

(deftest edit-article
  (let [app (t/make-app)
        token (t/login app)
        create-resp (t/POST app "/articles"
                      (t/article-params {"title" "Original Title" "content" "Original content"})
                      token)
        article-id (str/replace (t/redirect-location create-resp) "/article/" "")]
    (Thread/sleep 1100)
    (t/POST app (str "/article/" article-id)
      (t/article-params {"title" "Updated Title" "content" "Updated content"})
      token)
    (let [resp (t/GET app (str "/article/" article-id) token)
          html (t/parse resp)]
      (is (= 200 (:status resp)))
      (is (= "Updated Title" (t/text-of (t/select-one html (hs/tag :h1)))))
      (is (str/includes? (:body resp) "Updated content"))
      (is (not (str/includes? (:body resp) ">Original Title<"))
          "old title must not appear"))))

(deftest delete-article
  (let [app (t/make-app)
        token (t/login app)
        create-resp (t/POST app "/articles"
                      (t/article-params {"title" "To Delete" "content" "Will be gone"
                                         "publish" "1" "post-content" "Bye"})
                      token)
        article-id (str/replace (t/redirect-location create-resp) "/article/" "")]
    (testing "article exists before deletion"
      (is (= 200 (:status (t/GET app (str "/article/" article-id))))))
    (t/POST app (str "/article/" article-id "/delete") {} token)
    (testing "article returns 404 after soft-delete"
      (is (= 404 (:status (t/GET app (str "/article/" article-id))))))
    (testing "article appears in deleted list"
      (let [resp (t/GET app "/article/deleted" token)]
        (is (str/includes? (:body resp) "To Delete"))))
    (testing "home page is empty again"
      (is (str/includes? (:body (t/GET app "/")) "No articles yet.")))))

(deftest article-versioning
  (let [app (t/make-app)
        token (t/login app)]
    (t/POST app "/articles"
      (t/article-params {"title" "Versioned" "content" "v0 content"})
      token)
    (testing "first publish creates version 1"
      (Thread/sleep 1100)
      (t/POST app "/article/1"
        (t/article-params {"title" "Versioned" "content" "v1 content"
                           "publish" "1" "post-content" "Announcement"})
        token)
      (let [resp (t/GET app "/article/1/version/1")]
        (is (= 200 (:status resp)))
        (is (str/includes? (:body resp) "v1 content"))))
    (testing "second publish creates version 2"
      (Thread/sleep 1100)
      (t/POST app "/article/1"
        (t/article-params {"title" "Versioned" "content" "v2 content"
                           "publish" "1" "post-content" "Update"})
        token)
      (let [resp (t/GET app "/article/1/version/2")]
        (is (= 200 (:status resp)))
        (is (str/includes? (:body resp) "v2 content"))
        (is (not (str/includes? (:body resp) "v1 content")))))))

(deftest create-article-blank-title-rejected
  (let [app (t/make-app)
        token (t/login app)
        resp (t/POST app "/articles"
               (t/article-params {"title" "   " "content" "Body"})
               token)]
    (is (= 400 (:status resp)) "blank title must be rejected")))

(deftest publish-without-post-content-rejected
  (let [app (t/make-app)
        token (t/login app)
        resp (t/POST app "/articles"
               (t/article-params {"title" "No Post" "content" "Body"
                                  "publish" "1" "post-content" ""})
               token)]
    (is (= 400 (:status resp)))
    (is (str/includes? (:body resp) "Post content is required"))))

(deftest nonexistent-article-returns-404
  (let [app (t/make-app)
        resp (t/GET app "/article/999")]
    (is (= 404 (:status resp)))
    (is (str/includes? (:body resp) "Not Found"))))
