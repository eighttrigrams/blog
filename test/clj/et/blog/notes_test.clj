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

(defn- note-texts
  "What each Note reads as on the page. The body is rendered markdown, so this is
  the text of the rendered block rather than the stored string — see
  `stored-text` for the round-trip."
  [app admin]
  (->> (t/GET app "/notes" admin)
       t/parse
       (#(t/select-all % (hs/class "note-text")))
       ;; trimmed: the renderer ends a block with a newline, which is markup
       ;; rather than anything the Note says
       (map (comp str/trim t/text-of))))

(defn- stored-text
  "The Note's text as it is stored, read back off its edit form — the one place
  the page shows it unrendered."
  [app admin id]
  (-> (t/GET app (str "/notes/" id "/edit") admin)
      t/parse
      (t/select-one (hs/tag :textarea))
      t/text-of))

(deftest a-notes-user-may-deliver-a-note
  (let [app (t/make-app)
        [admin notes] (notes-credential! app)
        resp (t/POST-json app "/api/notes"
               {:text "From Telegram" :source "telegram"}
               notes)]
    (is (= 201 (:status resp)))
    (testing "and it is in the box, with where it came from"
      (let [body (:body (t/GET app "/notes" admin))]
        (is (str/includes? body "From Telegram"))
        (is (str/includes? body "telegram"))))))

(deftest a-note-is-one-text-and-keeps-its-shape
  (testing "a Note has no title to split off — what arrives is what is stored"
    (let [app (t/make-app)
          [admin notes] (notes-credential! app)
          text "First line\n\nand a second paragraph"]
      (is (= 201 (:status (t/POST-json app "/api/notes" {:text text} notes))))
      (is (= text (stored-text app admin 1))))))

(deftest the-owners-cookie-may-deliver-one-too
  (let [app (t/make-app)
        admin (t/login app)
        resp (t/POST-json-cookie app "/api/notes" {:text "By hand"} admin)]
    (is (= 201 (:status resp)))
    (is (= ["By hand"] (note-texts app admin)))))

(deftest delivering-a-note-needs-a-credential
  (let [app (t/make-app)
        [admin notes] (notes-credential! app)]
    (testing "no token at all"
      (is (= 401 (:status (t/POST-json app "/api/notes" {:text "Anonymous"})))))
    (testing "a bearer token that is not a token"
      (is (= 401 (:status (t/POST-json app "/api/notes" {:text "Forged"} "not-a-jwt")))))
    (testing "and nothing was written — while the same call with a token does write"
      (is (empty? (note-texts app admin)))
      (t/POST-json app "/api/notes" {:text "Credentialled"} notes)
      (is (= ["Credentialled"] (note-texts app admin))))))

(deftest a-revoked-notes-user-may-not-deliver
  (let [app (t/make-app)
        [admin notes] (notes-credential! app)
        id (-> (t/GET app "/notes-users" admin) :body
               (->> (re-find #"/notes-users/(\d+)/revoke")) second)]
    (is (= 201 (:status (t/POST-json app "/api/notes" {:text "While allowed"} notes))))
    (t/POST app (str "/notes-users/" id "/revoke") {} admin)
    (testing "the token it already holds stops working, without waiting for expiry"
      (is (= 401 (:status (t/POST-json app "/api/notes" {:text "After revoking"} notes)))))
    (is (= ["While allowed"] (note-texts app admin)))))

(deftest a-note-needs-text
  (let [app (t/make-app)
        [admin notes] (notes-credential! app)]
    (doseq [[label body] [["no text"    {:source "telegram"}]
                          ["blank text" {:text "   "}]
                          ["a non-string text" {:text 42}]
                          ["the old title field" {:title "Titled the old way"}]]]
      (is (= 400 (:status (t/POST-json app "/api/notes" body notes)))
          (str label " must be refused")))
    (is (empty? (note-texts app admin)))
    (testing "text is all it takes, though"
      (t/POST-json app "/api/notes" {:text "Texted"} notes)
      (is (= ["Texted"] (note-texts app admin))))))

;; A Note is written in the same editor as every other body here and read on the
;; same page, so it is rendered the same way. This also pins the escaping: the
;; text arrives from Telegram, and a Note is the one thing on this page written
;; somewhere other than this page.
(deftest a-note-is-markdown
  (let [app (t/make-app)
        [admin notes] (notes-credential! app)]
    (t/POST-json app "/api/notes"
      {:text "**bold** and a [link](https://example.com)\n\n- one\n- two"}
      notes)
    (let [body (:body (t/GET app "/notes" admin))]
      (testing "the marks are rendered, not shown"
        (is (str/includes? body "<strong>bold</strong>"))
        (is (str/includes? body "<li>one</li>"))
        (is (str/includes? body "href=\"https://example.com\""))
        (is (not (str/includes? body "**bold**"))))
      (testing "an outside link opens away from the box, as everywhere else here"
        (is (str/includes? body "rel=\"noopener\""))))
    (testing "a single newline is still a line break — a Note is often just lines"
      (t/POST-json app "/api/notes" {:text "one line\nand the next"} notes)
      (is (str/includes? (:body (t/GET app "/notes" admin)) "<br />")))
    (testing "and HTML in a Note is shown, never run"
      (t/POST-json app "/api/notes" {:text "<script>alert(1)</script>"} notes)
      (let [body (:body (t/GET app "/notes" admin))]
        (is (not (str/includes? body "<script>alert(1)</script>")))
        (is (str/includes? body "&lt;script&gt;"))))))

(deftest the-notes-box-is-the-owners-alone
  (let [app (t/make-app)
        [admin notes] (notes-credential! app)]
    (t/POST-json app "/api/notes" {:text "Private thought"} notes)
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
      (doseq [[path params] [["/notes" {"text" "Sneaky"}]
                             ["/notes/1" {"text" "Rewritten"}]
                             ["/notes/1/delete" {}]]]
        (let [resp (t/POST app path params)]
          (is (= 302 (:status resp)) (str "POST " path " must redirect"))
          (is (= "/login" (t/redirect-location resp)))))
      (is (= ["Private thought"] (note-texts app admin))))))

(deftest the-owner-adds-a-note-from-the-page
  (let [app (t/make-app)
        admin (t/login app)
        resp (t/POST app "/notes" {"text" "Typed in"} admin)]
    (is (= 302 (:status resp)))
    (is (= "/notes" (t/redirect-location resp)))
    (is (= ["Typed in"] (note-texts app admin)))
    (testing "a textless one is refused and nothing is added"
      (let [refused (t/POST app "/notes" {"text" " "} admin)]
        (is (= 400 (:status refused)))
        (is (str/includes? (:body refused) "needs some text"))
        (is (= ["Typed in"] (note-texts app admin)))))))

(deftest the-owner-edits-the-text
  (let [app (t/make-app)
        [admin notes] (notes-credential! app)]
    (t/POST-json app "/api/notes" {:text "As delivered"} notes)
    (testing "the edit form arrives filled in"
      (is (= "As delivered" (stored-text app admin 1))))
    (let [resp (t/POST app "/notes/1" {"text" "Edited"} admin)]
      (is (= 302 (:status resp)))
      (is (= "/notes" (t/redirect-location resp))))
    (is (= ["Edited"] (note-texts app admin)))
    (testing "an edit that would leave it textless changes nothing"
      (let [refused (t/POST app "/notes/1" {"text" ""} admin)]
        (is (= 400 (:status refused)))
        (is (= ["Edited"] (note-texts app admin)))))
    (testing "an unknown Note is a 404, not a 500"
      (is (= 404 (:status (t/GET app "/notes/999/edit" admin))))
      (is (= 404 (:status (t/POST app "/notes/999" {"text" "Ghost"} admin))))
      (is (= 404 (:status (t/GET app "/notes/nonsense/edit" admin)))))))

(deftest a-deleted-note-is-gone-for-good
  (let [app (t/make-app)
        admin (t/login app)]
    (t/POST app "/notes" {"text" "Keep"} admin)
    (t/POST app "/notes" {"text" "Delete me"} admin)
    (is (= #{"Keep" "Delete me"} (set (note-texts app admin))))
    (is (= 302 (:status (t/POST app "/notes/2/delete" {} admin))))
    (testing "the one deleted is gone and the other stayed"
      (is (= ["Keep"] (note-texts app admin))))
    (testing "and it is not kept anywhere — the row itself is away, so the id
              that named it names nothing"
      (is (= 404 (:status (t/GET app "/notes/2/edit" admin))))
      (is (= 404 (:status (t/POST app "/notes/2/delete" {} admin)))))
    (testing "an unknown Note cannot be deleted"
      (is (= 404 (:status (t/POST app "/notes/999/delete" {} admin)))))))

;; The one click that destroys something, and there is no undo behind it. Blog's
;; other destructive actions get a confirm *page*; this one is the inline idiom
;; Publish uses, so the guard is an attribute and this is what says it is there.
;; That it actually stops the POST is a browser matter — see the report.
(deftest deleting-asks-first
  (let [app (t/make-app)
        admin (t/login app)]
    (t/POST app "/notes" {"text" "Guarded"} admin)
    (let [button (-> (t/GET app "/notes" admin) t/parse
                     (t/select-one (hs/and (hs/tag :button) (hs/class "btn-danger"))))]
      (is (some? button) "the Delete button is the dangerous one, and says so")
      (is (str/includes? (get-in button [:attrs :onclick]) "confirm(")
          "a click must ask before it posts"))))

(deftest an-empty-box-says-so
  (let [app (t/make-app)
        admin (t/login app)]
    (is (str/includes? (:body (t/GET app "/notes" admin)) "The Notes box is empty"))))

(deftest notes-never-reach-the-public-api
  (testing "the Notes box is the owner's material, so no read serves it"
    (let [app (t/make-app)
          [admin notes] (notes-credential! app)]
      (t/POST-json app "/api/notes" {:text "Unlisted"} notes)
      (doseq [[label resp] [["a visitor" (t/GET app "/api/notes")]
                            ["a notes user" (t/GET-bearer app "/api/notes" notes)]]]
        (is (= 404 (:status resp)) (str "GET /api/notes must not read the box for " label))
        (is (not (str/includes? (:body resp) "Unlisted"))))
      (testing "and nothing else on the API mentions one"
        (doseq [path ["/api/articles" "/api/describe"]]
          (is (not (str/includes? (:body (t/GET app path)) "Unlisted")))))
      (is (= ["Unlisted"] (note-texts app admin))))))
