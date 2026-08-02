(ns et.blog.notes-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [hickory.select :as hs]
            [et.blog.test-support :as t]))

(defn- notes-credential!
  "An admin cookie and a usable notes-user token for the same app."
  [app]
  (let [admin (t/login app)]
    (t/POST app "/notes-users" {"name" "notes-user" "password" "pw"} admin)
    [admin (t/notes-token app "notes-user" "pw")]))

(defn- note-titles [app admin]
  (->> (t/GET app "/notes" admin)
       t/parse
       (#(t/select-all % (hs/tag :h2)))
       (map t/text-of)))

(deftest a-notes-user-may-deliver-a-note
  (let [app (t/make-app)
        [admin notes] (notes-credential! app)
        resp (t/POST-json app "/api/notes"
               {:title "From Telegram" :description "The body" :source "telegram"}
               notes)]
    (is (= 201 (:status resp)))
    (testing "and it is in the box, with its description and where it came from"
      (let [body (:body (t/GET app "/notes" admin))]
        (is (str/includes? body "From Telegram"))
        (is (str/includes? body "The body"))
        (is (str/includes? body "telegram"))))))

(deftest the-owners-cookie-may-deliver-one-too
  (let [app (t/make-app)
        admin (t/login app)
        resp (t/POST-json-cookie app "/api/notes" {:title "By hand"} admin)]
    (is (= 201 (:status resp)))
    (is (= ["By hand"] (note-titles app admin)))))

(deftest delivering-a-note-needs-a-credential
  (let [app (t/make-app)
        [admin notes] (notes-credential! app)]
    (testing "no token at all"
      (is (= 401 (:status (t/POST-json app "/api/notes" {:title "Anonymous"})))))
    (testing "a bearer token that is not a token"
      (is (= 401 (:status (t/POST-json app "/api/notes" {:title "Forged"} "not-a-jwt")))))
    (testing "and nothing was written — while the same call with a token does write"
      (is (empty? (note-titles app admin)))
      (t/POST-json app "/api/notes" {:title "Credentialled"} notes)
      (is (= ["Credentialled"] (note-titles app admin))))))

(deftest a-revoked-notes-user-may-not-deliver
  (let [app (t/make-app)
        [admin notes] (notes-credential! app)
        id (-> (t/GET app "/notes-users" admin) :body
               (->> (re-find #"/notes-users/(\d+)/revoke")) second)]
    (is (= 201 (:status (t/POST-json app "/api/notes" {:title "While allowed"} notes))))
    (t/POST app (str "/notes-users/" id "/revoke") {} admin)
    (testing "the token it already holds stops working, without waiting for expiry"
      (is (= 401 (:status (t/POST-json app "/api/notes" {:title "After revoking"} notes)))))
    (is (= ["While allowed"] (note-titles app admin)))))

(deftest a-note-needs-a-title
  (let [app (t/make-app)
        [admin notes] (notes-credential! app)]
    (doseq [[label body] [["no title"    {:description "orphan"}]
                          ["blank title" {:title "   "}]
                          ["a non-string title" {:title 42}]]]
      (is (= 400 (:status (t/POST-json app "/api/notes" body notes)))
          (str label " must be refused")))
    (is (empty? (note-titles app admin)))
    (testing "a title is all it takes, though"
      (t/POST-json app "/api/notes" {:title "Titled"} notes)
      (is (= ["Titled"] (note-titles app admin))))))

(deftest the-notes-box-is-the-owners-alone
  (let [app (t/make-app)
        [admin notes] (notes-credential! app)]
    (t/POST-json app "/api/notes" {:title "Private thought"} notes)
    (testing "a visitor is sent to the login page, not shown the box"
      (doseq [path ["/notes" "/notes/1/edit"]]
        (let [resp (t/GET app path)]
          (is (= 302 (:status resp)) (str "GET " path " must redirect"))
          (is (= "/login" (t/redirect-location resp)))
          (is (not (str/includes? (or (:body resp) "") "Private thought"))))))
    (testing "and a notes token buys no HTML access either"
      (let [resp (t/GET app "/notes" notes)]
        (is (= 302 (:status resp)))
        (is (= "/login" (t/redirect-location resp)))))
    (testing "the writes are guarded too, not just the reads"
      (doseq [[path params] [["/notes" {"title" "Sneaky"}]
                             ["/notes/1" {"title" "Rewritten"}]
                             ["/notes/1/done" {}]]]
        (let [resp (t/POST app path params)]
          (is (= 302 (:status resp)) (str "POST " path " must redirect"))
          (is (= "/login" (t/redirect-location resp)))))
      (is (= ["Private thought"] (note-titles app admin))))))

(deftest the-owner-adds-a-note-from-the-page
  (let [app (t/make-app)
        admin (t/login app)
        resp (t/POST app "/notes" {"title" "Typed in" "description" "Right here"} admin)]
    (is (= 302 (:status resp)))
    (is (= "/notes" (t/redirect-location resp)))
    (let [body (:body (t/GET app "/notes" admin))]
      (is (str/includes? body "Typed in"))
      (is (str/includes? body "Right here")))
    (testing "a titleless one is refused and nothing is added"
      (let [refused (t/POST app "/notes" {"title" " " "description" "no title"} admin)]
        (is (= 400 (:status refused)))
        (is (str/includes? (:body refused) "needs a title"))
        (is (= ["Typed in"] (note-titles app admin)))))))

(deftest the-owner-edits-both-title-and-description
  (let [app (t/make-app)
        [admin notes] (notes-credential! app)]
    (t/POST-json app "/api/notes" {:title "As delivered" :description "As delivered too"} notes)
    (testing "the edit form arrives filled in"
      (let [body (:body (t/GET app "/notes/1/edit" admin))]
        (is (str/includes? body "As delivered"))
        (is (str/includes? body "As delivered too"))))
    (let [resp (t/POST app "/notes/1" {"title" "Edited" "description" "Edited body"} admin)]
      (is (= 302 (:status resp)))
      (is (= "/notes" (t/redirect-location resp))))
    (let [body (:body (t/GET app "/notes" admin))]
      (is (str/includes? body "Edited"))
      (is (str/includes? body "Edited body"))
      (is (not (str/includes? body "As delivered"))))
    (testing "an edit that would leave it titleless changes nothing"
      (let [refused (t/POST app "/notes/1" {"title" "" "description" "Wiped"} admin)]
        (is (= 400 (:status refused)))
        (is (= ["Edited"] (note-titles app admin)))))
    (testing "an unknown Note is a 404, not a 500"
      (is (= 404 (:status (t/GET app "/notes/999/edit" admin))))
      (is (= 404 (:status (t/POST app "/notes/999" {"title" "Ghost"} admin))))
      (is (= 404 (:status (t/GET app "/notes/nonsense/edit" admin)))))))

(deftest a-note-marked-done-leaves-the-box
  (let [app (t/make-app)
        admin (t/login app)]
    (t/POST app "/notes" {"title" "Keep" "description" ""} admin)
    (t/POST app "/notes" {"title" "Finish" "description" ""} admin)
    (is (= #{"Keep" "Finish"} (set (note-titles app admin))))
    (let [id (-> (t/GET app "/notes" admin) :body
                 (->> (re-find #"/notes/(\d+)/done")) second)]
      (is (= 302 (:status (t/POST app (str "/notes/" id "/done") {} admin)))))
    (testing "one of them is gone and the other stayed"
      (is (= 1 (count (note-titles app admin)))))
    (testing "an unknown Note cannot be marked done"
      (is (= 404 (:status (t/POST app "/notes/999/done" {} admin)))))))

(deftest an-empty-box-says-so
  (let [app (t/make-app)
        admin (t/login app)]
    (is (str/includes? (:body (t/GET app "/notes" admin)) "The Notes box is empty"))))

(deftest notes-never-reach-the-public-api
  (testing "the Notes box is the owner's material, so no read serves it"
    (let [app (t/make-app)
          [admin notes] (notes-credential! app)]
      (t/POST-json app "/api/notes" {:title "Unlisted" :description "Unlisted body"} notes)
      (doseq [[label resp] [["a visitor" (t/GET app "/api/notes")]
                            ["a notes user" (t/GET-bearer app "/api/notes" notes)]]]
        (is (= 404 (:status resp)) (str "GET /api/notes must not read the box for " label))
        (is (not (str/includes? (:body resp) "Unlisted"))))
      (testing "and nothing else on the API mentions one"
        (doseq [path ["/api/articles" "/api/describe"]]
          (is (not (str/includes? (:body (t/GET app path)) "Unlisted body")))))
      (is (= ["Unlisted"] (note-titles app admin))))))
