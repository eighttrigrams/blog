(ns et.blog.test-support
  (:require [ring.mock.request :as mock]
            [ring.middleware.params :refer [wrap-params]]
            [clojure.string :as str]
            [et.blog.server :as server]
            [et.blog.db :as db]
            [hickory.core :as hc]
            [hickory.select :as hs]))

(defn- wrap-cookies [handler]
  (fn [req]
    (let [cookie-header (get-in req [:headers "cookie"] "")
          cookies (->> (str/split cookie-header #";\s*")
                       (filter #(str/includes? % "="))
                       (map #(let [[k v] (str/split % #"=" 2)] [k {:value v}]))
                       (into {}))]
      (handler (assoc req :cookies cookies)))))

(defn make-app []
  (when-let [old @server/ds]
    (when-let [pc (:persistent-conn old)]
      (.close pc)))
  (let [conn (db/init-conn {:type :sqlite-memory})]
    (reset! server/ds conn)
    (reset! server/*config {})
    (-> server/app-routes
        wrap-params
        wrap-cookies)))

(defn login [app]
  (let [resp (app (-> (mock/request :post "/login")
                      (mock/body {"password" "admin"})))]
    (when-let [cookie (get-in resp [:headers "Set-Cookie"])]
      (second (re-find #"token=([^;]+)" cookie)))))

(defn GET
  ([app path] (GET app path nil))
  ([app path token]
   (let [req (mock/request :get path)]
     (app (if token (mock/header req "cookie" (str "token=" token)) req)))))

(defn POST
  ([app path params] (POST app path params nil))
  ([app path params token]
   (let [req (-> (mock/request :post path) (mock/body params))]
     (app (if token (mock/header req "cookie" (str "token=" token)) req)))))

(defn parse [response]
  (-> (:body response) hc/parse hc/as-hickory))

(defn text-of [node]
  (cond
    (string? node) node
    (map? node) (apply str (map text-of (:content node)))
    (sequential? node) (apply str (map text-of node))
    :else ""))

(defn select-all [htree selector]
  (hs/select selector htree))

(defn select-one [htree selector]
  (first (hs/select selector htree)))

(defn redirect-location [resp]
  (get-in resp [:headers "Location"]))

(def article-defaults
  {"subtitle" "" "footnotes" "" "addenda" "" "preamble" ""
   "preview-image" "" "abstract" "" "topics" ""})

(defn article-params [overrides]
  (merge article-defaults overrides))

(defn create-and-publish! [app token overrides post-content]
  (let [create-resp (POST app "/article"
                      (article-params (assoc overrides "content" ""))
                      token)
        article-id (str/replace (get-in create-resp [:headers "Location"]) "/article/" "")]
    (Thread/sleep 1100)
    (POST app (str "/article/" article-id)
      (article-params (merge overrides {"save-version" "1"}))
      token)
    (Thread/sleep 1100)
    (POST app (str "/article/" article-id)
      (article-params (merge overrides {"publish" "1" "post-content" post-content}))
      token)
    article-id))
