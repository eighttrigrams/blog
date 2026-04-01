(ns et.blog.util
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]))

(defn- ordinal-suffix [day]
  (if (<= 11 day 13)
    "th"
    (case (mod day 10)
      1 "st" 2 "nd" 3 "rd" "th")))

(defn human-date [datetime-str]
  (when datetime-str
    (let [dt (LocalDateTime/parse datetime-str (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss"))
          day (.getDayOfMonth dt)
          month (.format dt (DateTimeFormatter/ofPattern "MMMM"))
          year (.getYear dt)]
      (str month " " day (ordinal-suffix day) ", " year))))
