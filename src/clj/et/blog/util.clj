(ns et.blog.util
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]))

(defn- ordinal-suffix [day]
  (if (<= 11 day 13)
    "th"
    (case (mod day 10)
      1 "st" 2 "nd" 3 "rd" "th")))

(def ^:private fmt-space (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss"))

(defn human-date [datetime-str]
  (when datetime-str
    (let [s (-> datetime-str
                (clojure.string/replace "T" " ")
                (clojure.string/replace #"\..*" "")
                (clojure.string/replace "Z" ""))
          dt (LocalDateTime/parse s fmt-space)
          day (.getDayOfMonth dt)
          month (.format dt (DateTimeFormatter/ofPattern "MMMM"))
          year (.getYear dt)]
      (str month " " day (ordinal-suffix day) ", " year))))

(defn human-datetime [datetime-str]
  (when datetime-str
    (let [s (-> datetime-str
                (clojure.string/replace "T" " ")
                (clojure.string/replace #"\..*" "")
                (clojure.string/replace "Z" ""))
          dt (LocalDateTime/parse s fmt-space)
          day (.getDayOfMonth dt)
          month (.format dt (DateTimeFormatter/ofPattern "MMMM"))
          year (.getYear dt)
          time (.format dt (DateTimeFormatter/ofPattern "HH:mm:ss"))]
      (str month " " day (ordinal-suffix day) ", " year " " time))))
