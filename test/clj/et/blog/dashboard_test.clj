(ns et.blog.dashboard-test
  "The interactivity switch, the owner's unsubscribe, and the event log."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.string :as str]
            [et.blog.test-support :as t]
            [et.blog.server :as server]
            [et.blog.db :as db]
            [et.blog.mail :as mail]
            [et.blog.tracker :as tracker]))

(def ^:dynamic *app* nil)

;; Nothing here may reach the network: an unconfigured forwarder and sender
;; are what the other suites already pin, and here they keep the tests offline.
(use-fixtures :each
  (fn [f]
    (tracker/configure! nil)
    (mail/configure! nil)
    (binding [*app* (t/make-app)] (f))))

(defn- ds [] @server/ds)

(deftest the-switch-defaults-to-live-and-round-trips
  (let [token (t/login *app*)]
    (is (= :live (db/interactivity (ds))) "a fresh site is live")
    (doseq [level ["deactivated" "hidden" "live"]]
      (t/POST *app* "/dashboard/settings" {"interactivity" level} token)
      (is (= (keyword level) (db/interactivity (ds)))
          (str "switch to " level)))))

(deftest an-unknown-level-is-refused-and-changes-nothing
  (let [token (t/login *app*)]
    (t/POST *app* "/dashboard/settings" {"interactivity" "hidden"} token)
    (let [resp (t/POST *app* "/dashboard/settings" {"interactivity" "banana"} token)]
      (is (= 400 (:status resp)))
      (is (= :hidden (db/interactivity (ds))) "the old level survives a bad write"))))

(deftest the-switch-is-owner-only
  ;; Without this the whole feature is decoration: anyone could turn it back on.
  (is (contains? #{302 401 403} (:status (t/POST *app* "/dashboard/settings"
                                                 {"interactivity" "hidden"} nil)))))

(deftest subscribing-is-refused-while-switched-off-but-leaving-is-not
  (let [token (t/login *app*)]
    (t/POST *app* "/email" {"email" "a@example.com" "action" "subscribe"})
    (is (= 1 (count (db/list-email-subscribers (ds)))))
    (t/POST *app* "/dashboard/settings" {"interactivity" "deactivated"} token)
    (testing "no new subscriptions"
      (let [resp (t/POST *app* "/email" {"email" "b@example.com" "action" "subscribe"})]
        (is (= 403 (:status resp)))
        (is (= 1 (count (db/list-email-subscribers (ds))))
            "and nobody was added")))
    (testing "but the people already on the list can still get off it"
      ;; Switching the feature off must not trap anyone.
      (t/POST *app* "/email" {"email" "a@example.com" "action" "unsubscribe"})
      (is (zero? (count (db/list-email-subscribers (ds))))))))

(deftest hidden-tells-visitors-nothing-while-deactivated-explains-itself
  (let [token (t/login *app*)]
    (t/POST *app* "/dashboard/settings" {"interactivity" "deactivated"} token)
    (let [body (:body (t/GET *app* "/email"))]
      (is (str/includes? body "temporarily switched off")
          "deactivated says so, so a visitor knows it is off rather than broken"))
    (t/POST *app* "/dashboard/settings" {"interactivity" "hidden"} token)
    (let [body (:body (t/GET *app* "/email"))]
      (is (not (str/includes? body "temporarily switched off"))
          "hidden leaves no trace of the feature at all"))))

(deftest events-are-recorded-and-survive-the-row-they-describe
  (t/POST *app* "/email" {"email" "gone@example.com" "action" "subscribe"})
  (let [token (t/login *app*)]
    (t/POST *app* "/dashboard/unsubscribe"
            {"email" "gone@example.com" "notify" "silent" "reason" "spam"} token)
    (is (zero? (count (db/list-email-subscribers (ds)))) "the row is gone")
    (let [events (db/list-events (ds))
          kinds (set (map :kind events))]
      (is (contains? kinds "subscribe"))
      (is (contains? kinds "unsubscribe-by-owner")
          "the log still knows it happened after the row went")
      (let [removal (first (filter #(= "unsubscribe-by-owner" (:kind %)) events))]
        (is (= "silent" (:notified removal)) "and records that nobody was told")
        (is (str/includes? (:detail removal) "spam"))))))

(deftest an-owner-unsubscribe-notifies-unless-told-otherwise
  (t/POST *app* "/email" {"email" "keep@example.com" "action" "subscribe"})
  (let [token (t/login *app*)]
    ;; No "notify" field at all — a stale form must not silently suppress the
    ;; mail the person is owed.
    (t/POST *app* "/dashboard/unsubscribe" {"email" "keep@example.com"} token)
    (let [removal (first (filter #(= "unsubscribe-by-owner" (:kind %))
                                 (db/list-events (ds))))]
      (is (= "mailed" (:notified removal))))))

(deftest the-dashboard-shows-its-sections-to-the-owner-only
  (let [token (t/login *app*)
        body (:body (t/GET *app* "/dashboard" token))]
    (doseq [section ["Interactivity" "Notes users" "Subscribers" "Log"]]
      (is (str/includes? body section) (str section " section is present"))))
  (is (contains? #{302 401 403} (:status (t/GET *app* "/dashboard" nil)))))

(deftest hidden-removes-comments-from-the-article-page-for-visitors-only
  (let [app (t/make-app)
        token (t/login app)
        article-id (t/create-and-publish! app token
                     {"title" "Commentable" "content" "Discuss me"}
                     "Published post")]
    (t/POST app (str "/article/" article-id "/version/1/comment")
            {"email" "reader@example.com" "display-name" "Reader" "body" "Great article!"})
    (let [path (str "/article/" article-id "/version/1")]
      (testing "live: the comment and the invitation are both there"
        (let [body (:body (t/GET app path))]
          (is (str/includes? body "Great article!"))
          (is (str/includes? body "Leave a comment"))))

      (testing "deactivated: the comment stays, the invitation becomes a notice"
        (t/POST app "/dashboard/settings" {"interactivity" "deactivated"} token)
        (let [body (:body (t/GET app path))]
          (is (str/includes? body "Great article!") "existing comments still readable")
          (is (not (str/includes? body "Leave a comment")))
          (is (str/includes? body "temporarily switched off"))))

      (testing "hidden: a visitor sees no comment and no hint one ever existed"
        (t/POST app "/dashboard/settings" {"interactivity" "hidden"} token)
        (let [body (:body (t/GET app path))]
          (is (not (str/includes? body "Great article!")))
          (is (not (str/includes? body "Leave a comment")))
          (is (not (str/includes? body "temporarily switched off"))
              "the notice is itself a trace, so it must not appear either")
          (is (not (str/includes? body "Comments")))))

      (testing "but the owner can still see and moderate them"
        ;; Otherwise hiding the feature would also lock him out of it.
        (let [body (:body (t/GET app path token))]
          (is (str/includes? body "Great article!"))))

      (testing "and a hand-rolled POST is refused, not merely unlinked"
        (let [resp (t/POST app (str "/article/" article-id "/version/1/comment")
                           {"email" "x@example.com" "display-name" "X" "body" "sneaky"})]
          (is (= 403 (:status resp))))))))

(deftest every-section-is-collapsed-by-default
  (let [app (t/make-app)
        token (t/login app)
        body (:body (t/GET app "/dashboard" token))]
    (doseq [id ["settings" "notes-users" "subscribers" "comments" "events"]]
      (is (re-find (re-pattern (str "<details[^>]*id=\"" id "\"")) body)
          (str id " is a details element"))
      (is (not (re-find (re-pattern (str "<details[^>]*id=\"" id "\"[^>]*open")) body))
          (str id " is closed to begin with")))))

(deftest the-comments-section-lists-what-people-left-on-articles
  (let [app (t/make-app)
        token (t/login app)
        article-id (t/create-and-publish! app token
                     {"title" "Commented On" "content" "Body"} "Post")]
    (t/POST app (str "/article/" article-id "/version/1/comment")
            {"email" "reader@example.com" "display-name" "Reader" "body" "A remark"})
    (let [body (:body (t/GET app "/dashboard" token))]
      (is (str/includes? body "A remark"))
      (is (str/includes? body "Reader"))
      (is (str/includes? body "Commented On") "and which article it was on")
      (is (str/includes? body "reader@example.com")
          "with the email, which is shown nowhere public")
      (is (str/includes? body (str "/comments/" 1 "/delete"))
          "and a way to remove it"))))

(deftest a-comment-is-titled-by-the-version-it-was-left-on
  ;; An article's title can change between versions. Showing today's title
  ;; against a comment on v1 would misattribute what was commented on.
  (let [app (t/make-app)
        token (t/login app)
        article-id (t/create-and-publish! app token
                     {"title" "Original Title" "content" "Body"} "Post")]
    (t/POST app (str "/article/" article-id "/version/1/comment")
            {"email" "r@example.com" "display-name" "R" "body" "On v1"})
    ;; Rename without a new version: the v1 row's title is what changes.
    (Thread/sleep 1100)
    (t/POST app (str "/article/" article-id)
            (t/article-params {"title" "Renamed" "content" "Body"}) token)
    (let [body (:body (t/GET app "/dashboard" token))]
      (is (str/includes? body "On v1") "the comment is still listed"))))
