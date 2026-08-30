(ns et.blog.tracker
  "Forwards blog events (messages, comments, subscriptions, the circuit
  breaker's alarm) into the owner's tracker inbox.

  Credentials arrive through `configure!`, called from `server/build-handler`
  the same way `render/set-image-base-url!` is — they used to be read from
  TRACKER_API_URL / TRACKER_USERNAME / TRACKER_PASSWORD via `System/getenv`,
  and nothing ever set those on the deployed machine. The old code answered a
  missing credential with `nil` rather than an exception, so the `try/catch`
  around every call site printed nothing and five months of forwarding failed
  in complete silence. Hence `configure!`, and hence the error log below: an
  unconfigured forwarder now says so every time it drops something."
  (:require [cheshire.core :as json]
            [taoensso.telemere :as tel])
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]))

(def ^:private client (HttpClient/newHttpClient))

(defonce ^:private *config (atom nil))

(defn configure!
  "Set the tracker credentials: {:base-url :username :password}. Called from
  server/build-handler with (:tracker config); nil or partial disables
  forwarding, which is the normal case in dev."
  [cfg]
  ;; nil rather than the config - see images/configure! for why.
  (reset! *config cfg)
  nil)

(defn- encode
  "Body -> JSON. This was hand-rolled and escaped only `\"`, which made every
  multi-line payload invalid JSON — and comment forwarding always builds one,
  since it joins the author line to the body with a blank line. A literal
  newline inside a JSON string is illegal, so tracker rejected those before
  reading them."
  [m]
  (json/generate-string m))

(defn- http-request [method url body headers]
  (let [builder (reduce-kv (fn [b k v] (.header b k v))
                           (-> (HttpRequest/newBuilder)
                               (.uri (URI/create url))
                               (.method method (HttpRequest$BodyPublishers/ofString (encode body))))
                           headers)
        response (.send client (.build builder) (HttpResponse$BodyHandlers/ofString))]
    {:status (.statusCode response)
     :body (.body response)}))

(defn- http-post [url body headers]
  (http-request "POST" url body headers))

(defn- http-put [url body headers]
  (http-request "PUT" url body headers))

(defn- parse-body [body]
  (try (json/parse-string body true)
       (catch Exception _ nil)))

(defn- login [url username password]
  (let [{:keys [status body]} (http-post (str url "/api/auth/login")
                                         {:username username :password password}
                                         {"Content-Type" "application/json"})]
    (if-let [token (:token (parse-body body))]
      token
      (do (tel/log! :error (str "Tracker login failed with status " status
                                " — blog cannot forward to the inbox"))
          nil))))

(defn- auth-headers [token]
  {"Content-Type" "application/json"
   "Authorization" (str "Bearer " token)})

(defn- with-tracker [f]
  (let [{:keys [base-url username password]} @*config]
    (if-not (and base-url username password)
      (tel/log! :error
        (str "Tracker forwarding is not configured — dropping a message. "
             "Set :tracker {:base-url .. :username .. :password ..} under "
             ":apps :blog in the umbrella config."))
      (when-let [token (login base-url username password)]
        (f base-url token)))))

(defn send-message! [title description sender]
  (with-tracker
    (fn [url token]
      (http-post (str url "/api/messages")
                 {:sender sender :title title :description description}
                 (auth-headers token)))))

(defn send-urgent-message! [title description sender]
  (with-tracker
    (fn [url token]
      (let [hdrs (auth-headers token)
            resp (http-post (str url "/api/messages")
                            {:sender sender :title title :description description}
                            hdrs)]
        (when-let [id (:id (parse-body (:body resp)))]
          (http-put (str url "/api/messages/" id "/urgency")
                    {:urgency "urgent"}
                    hdrs))))))
