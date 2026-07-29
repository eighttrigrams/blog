(ns et.blog.api-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [et.blog.test-support :as t]))

(def ^:private api-paths
  #{"/api/describe"
    "/api/articles"
    "/api/articles/:id"
    "/api/articles/:id/comments"
    "/api/articles/:id/versions"
    "/api/articles/:id/versions/:version"
    "/api/articles/:id/versions/:version/comments"})

(defn- publish-second-version! [app token overrides post-content]
  (Thread/sleep 1100)
  (t/POST app "/article/1"
    (t/article-params (merge overrides {"save-version" "1"}))
    token)
  (Thread/sleep 1100)
  (t/POST app "/article/1"
    (t/article-params (merge overrides {"publish" "1" "post-content" post-content}))
    token))

(deftest describe-lists-every-route
  (let [app (t/make-app)
        resp (t/GET app "/api/describe")
        routes (t/json-body resp)]
    (is (= 200 (:status resp)))
    (is (str/includes? (get-in resp [:headers "Content-Type"]) "application/json"))
    (is (= api-paths (set (map :path routes))))
    (testing "every route documents itself and none of them mutates"
      (doseq [{:keys [method doc name]} routes]
        (is (= "GET" method) (str name " must be a read"))
        (is (not (str/blank? doc)) (str name " must carry a docstring"))))))

(deftest listing-excludes-drafts-and-deleted
  (let [app (t/make-app)
        token (t/login app)]
    (t/create-and-publish! app token
      {"title" "Published One" "content" "Public body"} "Announcement")
    (t/POST app "/article"
      (t/article-params {"title" "A Draft" "content" "Draft body"})
      token)
    (let [doomed (t/create-and-publish! app token
                   {"title" "Doomed" "content" "Going away"} "Announcement")]
      (t/POST app (str "/article/" doomed "/delete") {} token))
    (let [articles (t/json-body (t/GET app "/api/articles"))]
      (is (= ["Published One"] (map :title articles)))
      (testing "a listing entry identifies the article and dates it"
        (let [article (first articles)]
          (is (= 1 (:article_id article)))
          (is (= 1 (:version article)))
          (is (= 1 (:latest_version article)))
          (is (some? (:latest_published_at article)))
          (is (some? (:created_at article)))))
      (testing "the listing is a listing: no article bodies in it"
        (is (not (contains? (first articles) :content)))))))

(deftest listing-is-newest-first
  (let [app (t/make-app)
        token (t/login app)]
    (t/create-and-publish! app token
      {"title" "Older" "content" "First"} "Announce older")
    (Thread/sleep 1100)
    (t/create-and-publish! app token
      {"title" "Newer" "content" "Second"} "Announce newer")
    (is (= ["Newer" "Older"] (map :title (t/json-body (t/GET app "/api/articles")))))))

(deftest single-article-serves-metadata-and-content
  (let [app (t/make-app)
        token (t/login app)
        _ (t/create-and-publish! app token
            {"title" "Readable" "content" "The body as stored"
             "subtitle" "A subtitle" "abstract" "An abstract" "topics" "swe"}
            "Announcement")
        resp (t/GET app "/api/articles/1")
        article (t/json-body resp)]
    (is (= 200 (:status resp)))
    (is (= "Readable" (:title article)))
    (is (= "A subtitle" (:subtitle article)))
    (is (= "An abstract" (:abstract article)))
    (is (= "swe" (:topics article)))
    (is (= 1 (:version article)))
    (testing "content comes back as the data model stores it, not as rendered HTML"
      (is (= "The body as stored" (:content article))))))

(deftest single-article-404s-for-draft-deleted-and-unknown
  (let [app (t/make-app)
        token (t/login app)
        draft-resp (t/POST app "/article"
                     (t/article-params {"title" "Hidden Draft" "content" "Secret"})
                     token)
        draft-id (str/replace (t/redirect-location draft-resp) "/article/" "")
        deleted-id (t/create-and-publish! app token
                     {"title" "Deleted Article" "content" "Gone"} "Announcement")]
    (t/POST app (str "/article/" deleted-id "/delete") {} token)
    (testing "a draft is nothing to the API"
      (let [resp (t/GET app (str "/api/articles/" draft-id))]
        (is (= 404 (:status resp)))
        (is (not (str/includes? (:body resp) "Secret")))))
    (testing "a deleted article is nothing to the API"
      (is (= 404 (:status (t/GET app (str "/api/articles/" deleted-id))))))
    (testing "an unknown id answers exactly the same"
      (let [unknown (t/GET app "/api/articles/999")]
        (is (= 404 (:status unknown)))
        (is (= (t/json-body (t/GET app (str "/api/articles/" draft-id)))
               (t/json-body unknown)))))
    (testing "an id that is not a number is unknown, not an error"
      (is (= 404 (:status (t/GET app "/api/articles/nonsense")))))))

(deftest versions-match-what-the-html-exposes
  (let [app (t/make-app)
        token (t/login app)]
    (t/create-and-publish! app token
      {"title" "Versioned" "content" "v1 content"} "Announce v1")
    (publish-second-version! app token
      {"title" "Versioned" "content" "v2 content"} "Announce v2")
    (let [versions (t/json-body (t/GET app "/api/articles/1/versions"))]
      (testing "published versions, newest first"
        (is (= [2 1] (map :version versions)))
        (is (every? :created_at versions)))
      (testing "an edit inside a version is not another version"
        (Thread/sleep 1100)
        (t/POST app "/article/1"
          (t/article-params {"title" "Versioned" "content" "v2 content, fixed typo"})
          token)
        (is (= [2 1] (map :version (t/json-body (t/GET app "/api/articles/1/versions")))))))
    (testing "each version serves its own content"
      (is (= "v1 content" (:content (t/json-body (t/GET app "/api/articles/1/versions/1")))))
      (is (= "v2 content, fixed typo"
             (:content (t/json-body (t/GET app "/api/articles/1/versions/2"))))))
    (testing "version 0 is the owner's working copy, so it does not exist here"
      (is (= 404 (:status (t/GET app "/api/articles/1/versions/0")))))
    (testing "a version that was never published 404s like the HTML page does"
      (is (= 404 (:status (t/GET app "/article/1/version/3"))))
      (is (= 404 (:status (t/GET app "/api/articles/1/versions/3")))))))

(deftest a-drafts-versions-are-not-listed
  (let [app (t/make-app)
        token (t/login app)]
    (t/POST app "/article"
      (t/article-params {"title" "Draft" "content" "Body"})
      token)
    (is (= 404 (:status (t/GET app "/api/articles/1/versions"))))
    (is (= 404 (:status (t/GET app "/api/articles/1/comments"))))))

(deftest comments-visibility-matches-the-html
  (let [app (t/make-app)
        token (t/login app)]
    (t/create-and-publish! app token
      {"title" "Discussed" "content" "v1 content"} "Announce v1")
    (publish-second-version! app token
      {"title" "Discussed" "content" "v2 content"} "Announce v2")
    (t/POST app "/article/1/version/1/comment"
      {"email" "alice@example.com" "display-name" "Alice" "body" "On v1"})
    (t/POST app "/article/1/version/2/comment"
      {"email" "bob@example.com" "display-name" "Bob" "body" "On v2"})
    (t/POST app "/comments/1/reply"
      {"email" "carol@example.com" "display-name" "Carol" "body" "Replying to Alice"})
    (testing "the article thread carries every version's comments, replies nested"
      (let [resp (t/GET app "/api/articles/1/comments")
            comments (t/json-body resp)]
        (is (= 200 (:status resp)))
        (is (= #{"Alice" "Bob"} (set (map :display_name comments))))
        (let [alice (first (filter #(= "Alice" (:display_name %)) comments))]
          (is (= [1] (map :article_version [alice])))
          (is (= ["Carol"] (map :display_name (:replies alice)))))
        (is (= [[]] (map :replies (filter #(= "Bob" (:display_name %)) comments))))))
    (testing "a version thread carries only its own version, as its page does"
      (let [v1 (t/json-body (t/GET app "/api/articles/1/versions/1/comments"))
            v2 (t/json-body (t/GET app "/api/articles/1/versions/2/comments"))]
        (is (= ["Alice"] (map :display_name v1)))
        (is (= ["Bob"] (map :display_name v2)))))
    (testing "commenter email addresses never leave the box"
      (doseq [path ["/api/articles/1/comments"
                    "/api/articles/1/versions/1/comments"]]
        (is (not (str/includes? (:body (t/GET app path)) "@example.com"))
            (str path " must not expose emails"))))
    (testing "no thread where there is no public version"
      (is (= 404 (:status (t/GET app "/api/articles/1/versions/0/comments"))))
      (is (= 404 (:status (t/GET app "/api/articles/1/versions/3/comments"))))
      (is (= 404 (:status (t/GET app "/api/articles/999/comments")))))))

(deftest a-deleted-articles-comments-go-with-it
  (let [app (t/make-app)
        token (t/login app)]
    (t/create-and-publish! app token
      {"title" "Doomed" "content" "v1"} "Announce")
    (t/POST app "/article/1/version/1/comment"
      {"email" "alice@example.com" "display-name" "Alice" "body" "On v1"})
    (is (= 1 (count (t/json-body (t/GET app "/api/articles/1/comments")))))
    (t/POST app "/article/1/delete" {} token)
    (is (= 404 (:status (t/GET app "/api/articles/1/comments"))))
    (is (= 404 (:status (t/GET app "/api/articles/1/versions/1/comments"))))))

(deftest the-api-is-read-only-and-leaves-the-html-alone
  (let [app (t/make-app)
        token (t/login app)]
    (t/create-and-publish! app token
      {"title" "Untouched" "content" "Body"} "Announcement")
    (testing "the blog still serves HTML at its own routes"
      (doseq [path ["/articles" "/article/1"]]
        (let [resp (t/GET app path)]
          (is (= 200 (:status resp)) (str path " must still be served"))
          (is (str/includes? (get-in resp [:headers "Content-Type"]) "text/html")
              (str path " must still be HTML")))))
    (testing "GET / still reaches the landing article, which no test DB has"
      (let [resp (t/GET app "/")]
        (is (str/includes? (get-in resp [:headers "Content-Type"]) "text/html"))
        (is (str/includes? (:body resp) "Not Found"))))
    (testing "an unknown path under /api answers in JSON, not with the HTML page"
      (let [resp (t/GET app "/api/nope")]
        (is (= 404 (:status resp)))
        (is (= {:error "Not found"} (t/json-body resp)))))
    (testing "there is nothing to post to"
      (is (= 404 (:status (t/POST app "/api/articles" {"title" "Injected"}))))
      (is (empty? (filter #(= "Injected" (:title %))
                          (t/json-body (t/GET app "/api/articles"))))))))
