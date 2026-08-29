(ns et.blog.tracker-test
  "The two faults that kept blog from ever reaching the tracker inbox, pinned.

  Neither needs a network: the encoder is pure, and an unconfigured forwarder
  is supposed to give up before it opens a connection."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [cheshire.core :as json]
            [et.blog.tracker :as tracker]))

(def ^:private encode #'et.blog.tracker/encode)

;; Every test here must leave the forwarder unconfigured, or a later one could
;; try to talk to whatever base-url it inherited.
(use-fixtures :each (fn [f] (tracker/configure! nil) (f) (tracker/configure! nil)))

(deftest encodes-a-multi-line-body-as-valid-json
  (testing "the payload comment forwarding actually builds"
    ;; The old hand-rolled encoder escaped `"` and nothing else, so the \n\n
    ;; this always contains went into the JSON string as literal newlines —
    ;; illegal, and rejected by tracker before it read the message.
    (let [description (str "From: Ada <ada@example.com>\n\nfirst line\nsecond line")
          payload {:sender "eighttrigrams.net"
                   :title "Blog comment on \"Guardrails Programming\" (v1)"
                   :description description}
          round-tripped (json/parse-string (encode payload) true)]
      (is (= payload round-tripped)
          "a body with newlines must survive the encoder intact")
      (is (= description (:description round-tripped))
          "the blank line between author and body is the whole point"))))

(deftest encodes-quotes-backslashes-and-control-characters
  (testing "the other characters the old encoder never escaped"
    (doseq [[label body] {"double quote"   "he said \"hello\""
                          "backslash"      "C:\\path\\to\\thing"
                          "tab"            "a\tb"
                          "carriage return" "a\r\nb"
                          "quote after backslash" "trailing \\\" pair"}]
      (testing label
        (let [payload {:sender "eighttrigrams.net" :title "t" :description body}]
          (is (= payload (json/parse-string (encode payload) true))
              (str label " must round-trip")))))))

(deftest an-unconfigured-forwarder-drops-the-message-instead-of-throwing
  (testing "no credentials configured"
    (tracker/configure! nil)
    ;; The call sites wrap this in try/catch and swallow, so throwing here
    ;; would be invisible; returning nil is what the callers expect. What must
    ;; NOT happen is an attempt to reach the network.
    (is (nil? (tracker/send-message! "t" "d" "s")))
    (is (nil? (tracker/send-urgent-message! "t" "d" "s"))))
  (testing "a partial credential is treated as no credential"
    ;; This is the shape the deployed machine was in for five months: a
    ;; base-url could be present while the username and password were not.
    (doseq [partial [{:base-url "http://127.0.0.1:1"}
                     {:base-url "http://127.0.0.1:1" :username "u"}
                     {:username "u" :password "p"}
                     {}]]
      (tracker/configure! partial)
      (is (nil? (tracker/send-message! "t" "d" "s"))
          (str "must not attempt a request with " (pr-str partial))))))
