(ns et.blog.tracker
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]))

(def ^:private client (HttpClient/newHttpClient))

(defn- json-str [m]
  (str "{"
       (clojure.string/join ","
         (map (fn [[k v]] (str "\"" (name k) "\":\"" (clojure.string/replace (str v) "\"" "\\\"") "\"")) m))
       "}"))

(defn- http-request [method url body headers]
  (let [builder (reduce-kv (fn [b k v] (.header b k v))
                           (-> (HttpRequest/newBuilder)
                               (.uri (URI/create url))
                               (.method method (HttpRequest$BodyPublishers/ofString (json-str body))))
                           headers)
        response (.send client (.build builder) (HttpResponse$BodyHandlers/ofString))]
    {:status (.statusCode response)
     :body (.body response)}))

(defn- http-post [url body headers]
  (http-request "POST" url body headers))

(defn- http-put [url body headers]
  (http-request "PUT" url body headers))

(defn- parse-token [body]
  (second (re-find #"\"token\"\s*:\s*\"([^\"]+)\"" body)))

(defn- login [url username password]
  (-> (http-post (str url "/api/auth/login")
                 {:username username :password password}
                 {"Content-Type" "application/json"})
      :body parse-token))

(defn- parse-id [body]
  (when-let [id-str (second (re-find #"\"id\"\s*:\s*(\d+)" body))]
    (Integer/parseInt id-str)))

(defn- auth-headers [token]
  {"Content-Type" "application/json"
   "Authorization" (str "Bearer " token)})

(defn- with-tracker [f]
  (let [url (System/getenv "TRACKER_API_URL")
        username (System/getenv "TRACKER_USERNAME")
        password (System/getenv "TRACKER_PASSWORD")]
    (when (and url username password)
      (when-let [token (login url username password)]
        (f url token)))))

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
        (when-let [id (parse-id (:body resp))]
          (http-put (str url "/api/messages/" id "/urgency")
                    {:urgency "urgent"}
                    hdrs))))))
