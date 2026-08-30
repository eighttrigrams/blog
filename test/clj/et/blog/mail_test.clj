(ns et.blog.mail-test
  "Pins the silent-failure bug that kept every outbound mail from being sent.

  Nothing here configures real credentials, so nothing here can send mail: an
  unconfigured sender is exactly what is under test."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [et.blog.mail :as mail]))

(use-fixtures :each (fn [f] (mail/configure! nil) (f) (mail/configure! nil)))

(deftest an-unconfigured-sender-drops-the-mail-instead-of-throwing
  (testing "no SMTP credentials"
    (mail/configure! nil)
    ;; delete-comment-handler wraps this in try/catch and swallows, so throwing
    ;; would be invisible. What must not happen is a connection attempt.
    (is (nil? (mail/send-plain-email! "someone@example.com" "subject" "body")))
    (is (nil? (mail/send-article-notification!
               [{:email "someone@example.com"}] "Title" "Sub" "content" "http://x")))))

(deftest a-partial-credential-is-treated-as-no-credential
  ;; The shape the deployed machine was in: a host present - it was even
  ;; hardcoded as a default - while user and password were not.
  (doseq [partial [{:host "smtp.example.com"}
                   {:host "smtp.example.com" :user "u"}
                   {:user "u" :password "p"}
                   {}]]
    (testing (pr-str partial)
      (mail/configure! partial)
      (is (nil? (mail/send-plain-email! "someone@example.com" "s" "b"))
          "must not attempt a connection")
      (is (nil? (mail/send-article-notification!
                 [{:email "someone@example.com"}] "T" "S" "c" "http://x"))
          "must not attempt a connection"))))

(deftest an-empty-subscriber-list-is-not-a-failure
  (testing "configured or not, nobody to mail is a no-op"
    (mail/configure! nil)
    (is (nil? (mail/send-article-notification! [] "T" "S" "c" "http://x")))
    (is (nil? (mail/send-article-notification! nil "T" "S" "c" "http://x")))))
