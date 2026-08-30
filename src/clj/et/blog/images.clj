(ns et.blog.images
  "Uploading a post's image straight onto the All-Inkl webspace, so writing a
  post does not mean starting remote-files-organizer first.

  **Deliberately a fraction of what the organizer can do.** That panel browses,
  renames, deletes and backs up, and it is host-only on purpose - its secrets.clj
  says a tool holding write access to the webspace should not be reachable from
  the internet. This runs in production, so it gets one verb and one place: store
  a file under blog-images/posts/<today>/. There is no listing, no rename, no
  delete, and no way to name a directory - the date comes from the clock and the
  prefix is a constant, so a caller cannot steer a write anywhere else.

  What is actually at stake is smaller than it first looks: the FTP login is
  jailed by the server to /daniel-de-oliveira.com/, which is the public web root
  of that site. Everything reachable here is already served to anyone who asks
  for it. Blog production also already holds the mailbox password it sends with,
  which is the larger prize of the two."
  (:require [clojure.string :as str]
            [taoensso.telemere :as tel])
  (:import [org.apache.commons.net.ftp FTP FTPSClient FTPReply]
           [java.io InputStream]
           [java.time LocalDate]))

(defonce ^:private *config (atom nil))

(defn configure!
  "Set the FTP credentials: {:host :username :password}. Called from
  server/build-handler with (:ftp config); nil or partial disables uploading,
  which is the normal case in dev."
  [cfg]
  ;; nil, not the config: reset! answers with the value it stored, and a REPL
  ;; or a `clojure -e` prints the value of every top-level form - which is how a
  ;; credential ends up on a terminal that was never asked to show one.
  (reset! *config cfg)
  nil)

(defn configured? []
  (let [{:keys [host username password]} @*config]
    (boolean (and host username password))))

;; No SVG. It is an image everywhere else in this list and a script host here,
;; and these are served from daniel-de-oliveira.com, where a stored script would
;; run as that origin.
(def ^:private allowed-extensions
  #{"jpg" "jpeg" "png" "gif" "webp" "avif"})

(def max-bytes (* 8 1024 1024))

(defn today-prefix
  "The one directory this can write to, derived from the clock rather than from
  anything a caller sends."
  []
  (str "blog-images/posts/" (str (LocalDate/now))))

(defn extension [filename]
  (let [n (str/lower-case (or filename ""))
        i (str/last-index-of n ".")]
    (when (and i (< i (dec (count n)))) (subs n (inc i)))))

(defn allowed-extension? [filename]
  (contains? allowed-extensions (extension filename)))

(defn safe-name
  "A filename reduced to something that cannot be a path. Everything outside
  [a-z0-9.-] becomes a dash, runs collapse, and leading dots go - so `..`, a
  slash, and a dotfile all stop being expressible before the name is ever joined
  to a directory."
  [filename]
  (let [base (-> (or filename "image")
                 (str/replace #"^.*[/\\\\]" "")          ; drop any path the client sent
                 str/lower-case
                 (str/replace #"[^a-z0-9.-]+" "-")
                 (str/replace #"-{2,}" "-")
                 (str/replace #"^[.-]+" ""))]
    (if (str/blank? base) "image" base)))

(defn- open ^FTPSClient [{:keys [host username password]}]
  (let [client (FTPSClient. false)]               ; false = explicit TLS (AUTH TLS)
    (.setControlEncoding client "UTF-8")
    (.setConnectTimeout client 15000)
    (.connect client ^String host 21)
    (when-not (FTPReply/isPositiveCompletion (.getReplyCode client))
      (.disconnect client)
      (throw (ex-info "FTP server refused the connection" {:host host})))
    (when-not (.login client username password)
      (.disconnect client)
      (throw (ex-info "FTP login rejected" {:host host})))
    ;; PROT P encrypts the data channel too - without it every uploaded byte
    ;; travels in the clear even though the login did not.
    (.execPBSZ client 0)
    (.execPROT client "P")
    (.setFileType client FTP/BINARY_FILE_TYPE)
    (.setSoTimeout client 30000)
    (.enterLocalPassiveMode client)
    client))

(defn- ensure-dir!
  "Walk the prefix and create what is missing. MKD answers an error for a
  directory that already exists, which is not a failure here - the test is
  whether we can change into it afterwards."
  [^FTPSClient client dir]
  (let [segments (remove str/blank? (str/split dir #"/"))]
    (reduce (fn [path segment]
              (let [next-path (str path "/" segment)]
                (when-not (.changeWorkingDirectory client next-path)
                  (.makeDirectory client next-path)
                  (when-not (.changeWorkingDirectory client next-path)
                    (throw (ex-info "could not create remote directory"
                                    {:dir next-path
                                     :reply (.getReplyString client)}))))
                next-path))
            ""
            segments)))

(defn upload!
  "Store `stream` as `filename` under today's post directory. Returns the
  relative path the post's image field wants - the same shape the existing rows
  carry - or throws. Returns nil, loudly, when no credential is configured."
  [^InputStream stream filename]
  (if-not (configured?)
    (tel/log! :error
      (str "FTP is not configured - refusing to upload " (pr-str filename) ". "
           "Set :ftp {:host .. :username .. :password ..} under :apps :blog."))
    (let [dir (today-prefix)
          name (safe-name filename)
          remote (str "/" dir "/" name)
          client (open @*config)]
      (try
        (ensure-dir! client dir)
        (when-not (.storeFile client remote stream)
          (throw (ex-info "FTP refused the upload"
                          {:path remote :reply (.getReplyString client)})))
        (str dir "/" name)
        (finally
          (try (.logout client) (catch Exception _))
          (try (.disconnect client) (catch Exception _)))))))
