(ns et.blog.articles-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hickory.select :as hs]
            [et.blog.test-support :as t]))

(deftest home-page-starts-empty
  (let [app (t/make-app)
        resp (t/GET app "/articles")
        html (t/parse resp)]
    (is (= 200 (:status resp)))
    (is (some? (t/select-one html (hs/find-in-text #"No articles yet\.")))
        "empty DB must show placeholder text")
    (is (empty? (t/select-all html (hs/class "article-list")))
        "no article-list element when empty")))

(deftest create-draft-article
  (let [app (t/make-app)
        token (t/login app)
        resp (t/POST app "/article"
               (t/article-params {"title" "Draft Article" "content" "Draft body"})
               token)]
    (is (= 302 (:status resp)))
    (is (str/starts-with? (t/redirect-location resp) "/article/"))
    (testing "draft does NOT appear on public home page"
      (let [home (t/GET app "/articles")]
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
        article-id (t/create-and-publish! app token
                     {"title" "Published Article" "content" "Article body here"
                      "subtitle" "A subtitle"}
                     "Post announcement text")]
    (testing "published article appears on public home page"
      (let [home (t/GET app "/articles")
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
        create-resp (t/POST app "/article"
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
        article-id (t/create-and-publish! app token
                     {"title" "To Delete" "content" "Will be gone"}
                     "Bye")]
    (testing "article exists before deletion"
      (is (= 200 (:status (t/GET app (str "/article/" article-id))))))
    (t/POST app (str "/article/" article-id "/delete") {} token)
    (testing "article returns 404 after soft-delete"
      (is (= 404 (:status (t/GET app (str "/article/" article-id))))))
    (testing "article appears in deleted list"
      (let [resp (t/GET app "/article/deleted" token)]
        (is (str/includes? (:body resp) "To Delete"))))
    (testing "home page is empty again"
      (is (str/includes? (:body (t/GET app "/articles")) "No articles yet.")))))

(deftest article-versioning
  (let [app (t/make-app)
        token (t/login app)]
    (t/create-and-publish! app token
      {"title" "Versioned" "content" "v1 content"} "Announcement")
    (testing "first publish creates version 1"
      (let [resp (t/GET app "/article/1/version/1")]
        (is (= 200 (:status resp)))
        (is (str/includes? (:body resp) "v1 content"))))
    (testing "save-version + publish creates version 2 and announces it"
      (Thread/sleep 1100)
      (t/POST app "/article/1"
        (t/article-params {"title" "Versioned" "content" "v2 content"
                           "save-version" "1"})
        token)
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
        resp (t/POST app "/article"
               (t/article-params {"title" "   " "content" "Body"})
               token)]
    (is (= 400 (:status resp)) "blank title must be rejected")))

(deftest publish-without-post-content-rejected
  (let [app (t/make-app)
        token (t/login app)]
    (t/POST app "/article"
      (t/article-params {"title" "No Post" "content" "Body"})
      token)
    (Thread/sleep 1100)
    (t/POST app "/article/1"
      (t/article-params {"title" "No Post" "content" "Body" "save-version" "1"})
      token)
    (let [resp (t/POST app "/article/1"
                 (t/article-params {"title" "No Post" "content" "Body"
                                    "publish" "1" "post-content" ""})
                 token)]
      (is (= 400 (:status resp)))
      (is (str/includes? (:body resp) "Post content is required")))))

(deftest topic-filtering
  (let [app (t/make-app)
        token (t/login app)]
    (t/create-and-publish! app token
      {"title" "Software Post" "content" "About code" "topics" "swe"}
      "SWE post")
    (t/create-and-publish! app token
      {"title" "Thinking Post" "content" "About ideas" "topics" "thoughts"}
      "Thoughts post")
    (testing "no filter shows all articles"
      (let [resp (t/GET app "/articles")]
        (is (str/includes? (:body resp) "Software Post"))
        (is (str/includes? (:body resp) "Thinking Post"))))
    (testing "filtering by swe shows only matching article"
      (let [resp (t/GET app "/articles?topic=swe")]
        (is (str/includes? (:body resp) "Software Post"))
        (is (not (str/includes? (:body resp) "Thinking Post")))))
    (testing "filtering is case insensitive"
      (let [resp (t/GET app "/articles?topic=SWE")]
        (is (str/includes? (:body resp) "Software Post"))))
    (testing "filtering by unknown topic shows none"
      (let [resp (t/GET app "/articles?topic=nope")]
        (is (not (str/includes? (:body resp) "Software Post")))
        (is (not (str/includes? (:body resp) "Thinking Post")))))))

(deftest save-new-version-bumps-version
  (let [app (t/make-app)
        token (t/login app)]
    (t/create-and-publish! app token
      {"title" "Versioned" "content" "v1 content"}
      "Announcement")
    (Thread/sleep 1100)
    (t/POST app "/article/1"
      (t/article-params {"title" "Versioned" "content" "v2 content"
                         "save-version" "1"})
      token)
    (testing "save-version creates a new version row"
      (let [resp (t/GET app "/article/1/version/2")]
        (is (= 200 (:status resp)))
        (is (str/includes? (:body resp) "v2 content"))))
    (testing "save-version does not create an announcement post"
      (let [resp (t/GET app "/posts")]
        (is (= 1 (count (t/select-all (t/parse resp)
                          (hs/descendant (hs/class "post-list") (hs/tag :li))))))))))

(deftest cannot-bump-version-when-current-not-published
  (let [app (t/make-app)
        token (t/login app)]
    (t/create-and-publish! app token
      {"title" "T" "content" "v1"} "Announce v1")
    (Thread/sleep 1100)
    (t/POST app "/article/1"
      (t/article-params {"title" "T" "content" "v2 content" "save-version" "1"})
      token)
    (testing "edit page hides Save new version while v2 is unpublished"
      (let [resp (t/GET app "/article/1/edit" token)]
        (is (= 200 (:status resp)))
        (is (not (str/includes? (:body resp) "Save new version")))
        (is (str/includes? (:body resp) ">Publish<"))))
    (testing "server rejects another save-version"
      (let [resp (t/POST app "/article/1"
                   (t/article-params {"title" "T" "content" "v3 content" "save-version" "1"})
                   token)]
        (is (= 400 (:status resp)))
        (is (str/includes? (:body resp) "Publish the current version"))))))

(deftest publish-validation-error-keeps-publish-button
  (let [app (t/make-app)
        token (t/login app)]
    (t/POST app "/article"
      (t/article-params {"title" "T" "content" ""})
      token)
    (Thread/sleep 1100)
    (t/POST app "/article/1"
      (t/article-params {"title" "T" "content" "v1" "save-version" "1"})
      token)
    (let [resp (t/POST app "/article/1"
                 (t/article-params {"title" "T" "content" "v1"
                                    "publish" "1" "post-content" ""})
                 token)]
      (is (= 400 (:status resp)))
      (is (str/includes? (:body resp) "Post content is required"))
      (testing "Publish button remains visible after validation error"
        (is (str/includes? (:body resp) ">Publish<"))))))

(deftest cannot-publish-same-version-twice
  (let [app (t/make-app)
        token (t/login app)]
    (t/create-and-publish! app token
      {"title" "T" "content" "v1"} "Announce v1")
    (testing "edit page hides Publish button on already-published version"
      (let [resp (t/GET app "/article/1/edit" token)]
        (is (= 200 (:status resp)))
        (is (not (str/includes? (:body resp) ">Publish<")))
        (is (str/includes? (:body resp) "Save new version"))))
    (testing "server rejects a second publish on the same version"
      (let [resp (t/POST app "/article/1"
                   (t/article-params {"title" "T" "content" "v1"
                                      "publish" "1" "post-content" "again"})
                   token)]
        (is (= 400 (:status resp)))
        (is (str/includes? (:body resp) "already been published"))))))

(deftest publish-does-not-bump-version
  (let [app (t/make-app)
        token (t/login app)]
    (t/create-and-publish! app token
      {"title" "T" "content" "v1"} "Announce")
    (Thread/sleep 1100)
    (t/POST app "/article/1"
      (t/article-params {"title" "T" "content" "v1 edited"
                         "save-version" "1"})
      token)
    (testing "republishing does not bump beyond the user-controlled version"
      (is (= 200 (:status (t/GET app "/article/1/version/2"))))
      (is (= 404 (:status (t/GET app "/article/1/version/3")))))))

(deftest save-new-version-noop-when-unchanged
  (let [app (t/make-app)
        token (t/login app)]
    (t/create-and-publish! app token
      {"title" "Same" "content" "same content"} "Announcement")
    (Thread/sleep 1100)
    (t/POST app "/article/1"
      (t/article-params {"title" "Same" "content" "same content"
                         "save-version" "1"})
      token)
    (testing "no v2 created when content is unchanged"
      (is (= 404 (:status (t/GET app "/article/1/version/2")))))
    (testing "v1 still latest"
      (let [resp (t/GET app "/article/1")]
        (is (str/includes? (:body resp) "v1"))
        (is (not (str/includes? (:body resp) "v2")))))))

(deftest drafts-page-shows-preview-image
  (let [app (t/make-app)
        token (t/login app)]
    (t/POST app "/article"
      (t/article-params {"title" "Draft With Image" "content" "Body"
                         "preview-image" "blog-images/1/cover.png"})
      token)
    (let [resp (t/GET app "/article/drafts" token)
          html (t/parse resp)
          imgs (t/select-all html (hs/class "article-preview"))]
      (is (= 200 (:status resp)))
      (is (= 1 (count imgs)))
      (is (str/includes? (or (get-in (first imgs) [:attrs :src]) "")
                         "blog-images/1/cover.png")))))

(deftest articles-feed-has-one-entry-per-version
  (let [app (t/make-app)
        token (t/login app)]
    (t/create-and-publish! app token
      {"title" "Feed Article" "content" "v1"} "Announce v1")
    (Thread/sleep 1100)
    (t/POST app "/article/1"
      (t/article-params {"title" "Feed Article" "content" "v1 edited"})
      token)
    (let [resp (t/GET app "/feed/articles.xml")
          body (:body resp)]
      (is (= 200 (:status resp)))
      (testing "in-version saves do not multiply feed entries"
        (is (= 1 (count (re-seq #"<entry>" body)))))
      (testing "feed reflects the latest in-version content"
        (is (str/includes? body "/article/1/version/1"))))))

(deftest articles-feed-only-includes-published-versions
  (let [app (t/make-app)
        token (t/login app)]
    (t/create-and-publish! app token
      {"title" "Feed Article" "content" "v1"} "Announce v1")
    (Thread/sleep 1100)
    (t/POST app "/article/1"
      (t/article-params {"title" "Feed Article" "content" "v2 content"
                         "save-version" "1"})
      token)
    (let [body (:body (t/GET app "/feed/articles.xml"))]
      (testing "v2 (bumped but not published) is not in the feed"
        (is (= 1 (count (re-seq #"<entry>" body))))
        (is (str/includes? body "/article/1/version/1"))
        (is (not (str/includes? body "/article/1/version/2")))))
    (Thread/sleep 1100)
    (t/POST app "/article/1"
      (t/article-params {"title" "Feed Article" "content" "v2 content"
                         "publish" "1" "post-content" "Announce v2"})
      token)
    (let [body (:body (t/GET app "/feed/articles.xml"))]
      (testing "after publishing v2, both versions appear"
        (is (= 2 (count (re-seq #"<entry>" body))))
        (is (str/includes? body "/article/1/version/2"))))))

(deftest nonexistent-article-returns-404
  (let [app (t/make-app)
        resp (t/GET app "/article/999")]
    (is (= 404 (:status resp)))
    (is (str/includes? (:body resp) "Not Found"))))

;; The in-between save that Zen's cmd+9 uses. It is the plain update, with the
;; response envelope swapped for one a fetch can read — never a version bump.

(deftest no-redirect-save-answers-204-and-keeps-the-version
  (let [app (t/make-app)
        token (t/login app)]
    (t/create-and-publish! app token
      {"title" "Zen" "content" "v1 content"} "Announce")
    (Thread/sleep 1100)
    (let [resp (t/POST app "/article/1"
                 (t/article-params {"title" "Zen" "content" "written in zen"
                                    "no-redirect" "1"})
                 token)]
      (is (= 204 (:status resp)) "success is 204, the one status the page trusts")
      (is (str/blank? (str (:body resp))) "204 carries no body")
      (is (nil? (t/redirect-location resp)) "nothing for fetch to follow"))
    (testing "the new content is in the DB"
      (let [resp (t/GET app "/article/1" token)]
        (is (str/includes? (:body resp) "written in zen"))
        (is (not (str/includes? (:body resp) "v1 content")))))
    (testing "the version is unchanged"
      (is (= 200 (:status (t/GET app "/article/1/version/1"))))
      (is (= 404 (:status (t/GET app "/article/1/version/2")))
          "an in-between save must not bump a version"))))

(deftest no-redirect-save-refuses-a-blank-title-in-plain-text
  (let [app (t/make-app)
        token (t/login app)]
    (t/POST app "/article"
      (t/article-params {"title" "Zen" "content" "kept content"})
      token)
    (Thread/sleep 1100)
    (let [resp (t/POST app "/article/1"
                 (t/article-params {"title" "   " "content" "should not land"
                                    "no-redirect" "1"})
                 token)]
      (is (= 400 (:status resp)))
      (is (not (str/includes? (str (:body resp)) "<html"))
          "a refusal is a short reason, not the whole edit page")
      (is (str/includes? (str (get-in resp [:headers "Content-Type"])) "text/plain")))
    (testing "the old content is untouched"
      (let [resp (t/GET app "/article/1/edit" token)]
        (is (str/includes? (:body resp) "kept content"))
        (is (not (str/includes? (:body resp) "should not land")))))))

(deftest no-redirect-save-without-a-token-is-not-204
  (let [app (t/make-app)
        token (t/login app)]
    (t/POST app "/article"
      (t/article-params {"title" "Zen" "content" "kept content"})
      token)
    (Thread/sleep 1100)
    (let [resp (t/POST app "/article/1"
                 (t/article-params {"title" "Zen" "content" "sneaked in"
                                    "no-redirect" "1"}))]
      (is (not= 204 (:status resp))
          "a stale session must read as failure, so the page can show the red X"))
    (testing "nothing was written"
      (let [resp (t/GET app "/article/1/edit" token)]
        (is (str/includes? (:body resp) "kept content"))
        (is (not (str/includes? (:body resp) "sneaked in")))))))

;; The guard is "has an id", not "is published": a draft is an existing article
;; at version 0, so it gets Zen exactly the way it gets Delete.

(deftest edit-page-offers-zen-only-for-an-existing-article
  (let [app (t/make-app)
        token (t/login app)]
    (t/POST app "/article"
      (t/article-params {"title" "Zen" "content" "body text"})
      token)
    (testing "a draft's edit page renders the button and the overlay"
      (let [resp (t/GET app "/article/1/edit" token)
            html (t/parse resp)
            button (t/select-one html (hs/id "zen-open"))
            overlay (t/select-one html (hs/id "zen-overlay"))
            mount (t/select-one html (hs/id "zen-content"))
            scripts (map #(get-in % [:attrs :src]) (t/select-all html (hs/tag :script)))]
        (is (= 200 (:status resp)))
        (is (str/includes? (:body (t/GET app "/article/drafts" token)) "Zen")
            "the article under test is a draft, so Zen is not gated on publication")
        (is (some? button) "a fourth button in .edit-actions")
        (is (= "Zen" (t/text-of button)))
        (is (= "button" (get-in button [:attrs :type]))
            "a submit button sitting in the form would post the article")
        (is (some? overlay) "the overlay is server-rendered")
        (is (str/includes? (str (get-in overlay [:attrs :style])) "display: none")
            "hidden until opened")
        (is (empty? (t/select-all html (hs/descendant (hs/tag :form) (hs/id "zen-overlay"))))
            "outside the form, so nothing in it can submit")
        ;; The writing surface is a CodeMirror view now. What used to be carried
        ;; by "a textarea with no name" is carried by there being no form control
        ;; in there at all: a div holds nothing that could be serialized.
        (is (some? mount) "the editor's mount element")
        (is (= :div (:tag mount)) "a mount element, not a form control")
        (is (empty? (t/select-all html (hs/descendant (hs/id "zen-overlay")
                                         (hs/or (hs/tag :textarea) (hs/tag :input) (hs/tag :select)))))
            "nothing in the overlay can be submitted, wherever it ends up")
        (is (some? (t/select-one html (hs/id "zen-close"))) "the X that is the only way out")
        (testing "the editor's scripts are loaded"
          (is (some #{"/vendor/codemirror/codemirror.js"} scripts) "the CodeMirror bundle")
          (is (some #{"/js/zen-motions.js"} scripts) "the motions")
          (is (some #{"/js/zen.js"} scripts) "the Zen wiring")
          (doseq [src ["/vendor/codemirror/codemirror.js" "/js/zen-motions.js" "/js/zen.js"]]
            (is (some? (io/resource (str "public/blog" src)))
                (str src " must exist on the classpath, not just in the markup"))))))
    (testing "a published article's edit page renders them just the same"
      (let [id (t/create-and-publish! app token
                 {"title" "Published Zen" "content" "body"} "Announce")
            html (t/parse (t/GET app (str "/article/" id "/edit") token))]
        (is (some? (t/select-one html (hs/id "zen-open"))))
        (is (some? (t/select-one html (hs/id "zen-overlay"))))))
    (testing "a new article offers neither"
      (let [resp (t/GET app "/article/new" token)
            html (t/parse resp)]
        (is (= 200 (:status resp)))
        (is (nil? (t/select-one html (hs/id "zen-open")))
            "a new article has no id, so an in-between save has nowhere to write")
        (is (nil? (t/select-one html (hs/id "zen-overlay"))))
        (is (not (str/includes? (:body resp) "codemirror.js"))
            "and it does not pay 270KB for an editor it cannot open")))))
