(ns et.blog.notes-users-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [hickory.select :as hs]
            [et.blog.test-support :as t]))

(defn- create-notes-user!
  "Create one through the admin page and read the password back out of the page
  that shows it once. Returns [name password]."
  [app token name]
  (let [resp (t/POST app "/notes-users" {"name" name "password" ""} token)
        shown (t/text-of (t/select-one (t/parse resp) (hs/tag :code)))]
    (is (= 200 (:status resp)))
    [name shown]))

(deftest a-notes-user-is-created-with-a-password-shown-exactly-once
  (let [app (t/make-app)
        token (t/login app)
        [name password] (create-notes-user! app token "notes-user")]
    (is (not (str/blank? password)))
    (testing "the page says the password cannot be recovered"
      (let [body (:body (t/POST app "/notes-users" {"name" "second" "password" ""} token))]
        (is (str/includes? body "cannot be recovered"))))
    (testing "and it is gone from every later render of the page"
      (let [body (:body (t/GET app "/notes-users" token))]
        (is (str/includes? body name))
        (is (not (str/includes? body password)))))
    (testing "the shown password is the one that logs in"
      (is (some? (t/notes-token app name password))))))

(deftest a-given-password-is-kept-as-given
  (testing "so the owner can make the row agree with the baked plurama-cli secret"
    (let [app (t/make-app)
          token (t/login app)]
      (t/POST app "/notes-users" {"name" "notes-user" "password" "chosen-by-the-owner"} token)
      (is (some? (t/notes-token app "notes-user" "chosen-by-the-owner"))))))

(deftest names-are-unique
  (let [app (t/make-app)
        token (t/login app)]
    (create-notes-user! app token "notes-user")
    (let [resp (t/POST app "/notes-users" {"name" "notes-user" "password" ""} token)]
      (is (= 400 (:status resp)))
      (is (str/includes? (:body resp) "already a notes user")))))

(deftest a-name-is-required
  (let [app (t/make-app)
        token (t/login app)
        resp (t/POST app "/notes-users" {"name" "  " "password" ""} token)]
    (is (= 400 (:status resp)))
    (is (str/includes? (:body resp) "Please enter a name"))))

(deftest login-answers-the-documented-contract
  (let [app (t/make-app)
        token (t/login app)
        [name password] (create-notes-user! app token "notes-user")
        resp (t/POST-json app "/api/auth/login" {:username name :password password})]
    (is (= 200 (:status resp)))
    (is (str/includes? (get-in resp [:headers "Content-Type"]) "application/json"))
    (testing "{:token ...} — the shape plurama's app-client requires"
      (is (string? (:token (t/json-body resp)))))))

(deftest login-refuses-everything-else-alike
  (let [app (t/make-app)
        token (t/login app)
        [name password] (create-notes-user! app token "notes-user")]
    (doseq [[label body] [["a wrong password"   {:username name :password "nope"}]
                          ["an unknown name"    {:username "nobody" :password password}]
                          ["a missing password" {:username name}]
                          ["nothing at all"     {}]]]
      (let [resp (t/POST-json app "/api/auth/login" body)]
        (is (= 401 (:status resp)) (str label " must not log in"))
        (is (nil? (:token (t/json-body resp))) (str label " must not yield a token"))))))

(deftest revoking-keeps-the-row-and-stops-the-login
  (let [app (t/make-app)
        token (t/login app)
        [name password] (create-notes-user! app token "notes-user")
        id (-> (t/GET app "/notes-users" token) :body
               (->> (re-find #"/notes-users/(\d+)/revoke")) second)]
    (is (some? id))
    (is (= 302 (:status (t/POST app (str "/notes-users/" id "/revoke") {} token))))
    (testing "the history stays visible on the page"
      (let [body (:body (t/GET app "/notes-users" token))]
        (is (str/includes? body name))
        (is (str/includes? body "revoked"))))
    (testing "but the credential no longer logs in"
      (is (= 401 (:status (t/POST-json app "/api/auth/login"
                            {:username name :password password})))))))

(deftest a-notes-token-is-not-a-login
  (testing "both tokens are signed with the same secret, so the claim must decide"
    (let [app (t/make-app)
          admin-token (t/login app)
          [name password] (create-notes-user! app admin-token "notes-user")
          notes (t/notes-token app name password)]
      (doseq [path ["/notes-users" "/article/drafts" "/article/new"]]
        (let [resp (t/GET app path notes)]
          (is (= 302 (:status resp)) (str "GET " path " must not accept a notes token"))
          (is (= "/login" (t/redirect-location resp))))))))

(deftest the-notes-users-page-is-admin-only
  (let [app (t/make-app)]
    (doseq [[method resp] [["GET" (t/GET app "/notes-users")]
                           ["POST" (t/POST app "/notes-users" {"name" "sneaky"})]
                           ["POST" (t/POST app "/notes-users/1/revoke" {})]]]
      (is (= 302 (:status resp)) (str method " /notes-users must redirect when unauthenticated"))
      (is (= "/login" (t/redirect-location resp))))
    (testing "and the unauthenticated request created nothing"
      (let [token (t/login app)]
        (is (str/includes? (:body (t/GET app "/notes-users" token)) "No notes users yet"))))))

(deftest the-nav-shows-the-notes-links-only-to-the-owner
  (let [app (t/make-app)
        token (t/login app)]
    ;; Notes users became a section of the dashboard, so the owner-only link
    ;; in the nav is /dashboard now. What is under test is unchanged: that
    ;; these links appear for the owner and for nobody else.
    (testing "a visitor sees neither"
      (let [body (:body (t/GET app "/articles"))]
        (is (not (str/includes? body "/dashboard")))
        (is (not (str/includes? body "\"/notes\"")))))
    (testing "the owner sees both"
      (let [body (:body (t/GET app "/articles" token))]
        (is (str/includes? body "/dashboard"))
        (is (str/includes? body "\"/notes\""))))))
