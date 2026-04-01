(ns et.blog.tracker
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]))

(def ^:private client (HttpClient/newHttpClient))

(defn- json-str [m]
  (str "{"
       (clojure.string/join ","
         (map (fn [[k v]] (str "\"" (name k) "\":\"" (clojure.string/replace (str v) "\"" "\\\"") "\"")) m))
       "}"))

(defn- http-post [url body headers]
  (let [builder (reduce-kv (fn [b k v] (.header b k v))
                           (-> (HttpRequest/newBuilder)
                               (.uri (URI/create url))
                               (.POST (HttpRequest$BodyPublishers/ofString (json-str body))))
                           headers)
        response (.send client (.build builder) (HttpResponse$BodyHandlers/ofString))]
    {:status (.statusCode response)
     :body (.body response)}))

(defn- parse-token [body]
  (second (re-find #"\"token\"\s*:\s*\"([^\"]+)\"" body)))

(defn- login [url username password]
  (-> (http-post (str url "/api/auth/login")
                 {:username username :password password}
                 {"Content-Type" "application/json"})
      :body parse-token))

(defn send-message! [title description sender]
  (let [url (System/getenv "TRACKER_API_URL")
        username (System/getenv "TRACKER_USERNAME")
        password (System/getenv "TRACKER_PASSWORD")]
    (when (and url username password)
      (let [token (login url username password)]
        (when token
          (http-post (str url "/api/messages")
                     {:sender sender :title title :description description}
                     {"Content-Type" "application/json"
                      "Authorization" (str "Bearer " token)}))))))
