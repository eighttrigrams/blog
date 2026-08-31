(ns et.blog.images
  "Uploading a post's image straight onto the All-Inkl webspace, so writing a
  post does not mean starting remote-files-organizer first.

  **Deliberately a fraction of what the organizer can do.** That panel browses,
  renames, deletes and backs up, and it is host-only on purpose - its secrets.clj
  says a tool holding write access to the webspace should not be reachable from
  the internet. This runs in production, so it gets one verb and one place: store
  a file under blog-images/posts/<post-id>/, and read back what is in that one
  directory. There is no rename and no delete, nothing outside a post's own
  folder, and no way to *name* a directory: the prefix is a constant and the only
  variable is a post id, which the caller coerces to an integer before it gets
  here - so a path cannot be smuggled through it.

  What is actually at stake is smaller than it first looks: the FTP login is
  jailed by the server to /daniel-de-oliveira.com/, which is the public web root
  of that site. Everything reachable here is already served to anyone who asks
  for it. Blog production also already holds the mailbox password it sends with,
  which is the larger prize of the two."
  (:require [clojure.string :as str]
            [taoensso.telemere :as tel])
  (:import [org.apache.commons.net.ftp FTP FTPFile FTPSClient FTPReply]
           [java.io InputStream]))

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

(defn post-prefix
  "The one directory this can write to for a given post. `post-id` must already
  be an integer - the handler parses it out of the route - so the whole path is
  a constant prefix plus a number and there is nothing here a caller can steer.

  Keyed by post rather than by date, which is also how articles have always done
  it (blog-images/<article-id>/...). Existing rows under the old dated
  directories are untouched: they store their own path and keep resolving."
  [post-id]
  (when-not (integer? post-id)
    (throw (ex-info "post-id must be an integer" {:post-id post-id})))
  (str "blog-images/posts/" post-id))

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
    ;; Passive FTP hands back the address to open the data channel on, and the
    ;; server answers with its own IPv4 - 227 Entering Passive Mode (85,13,...).
    ;; A fly machine is IPv6-only with NAT64 for outbound v4, so it reaches this
    ;; host as 64:ff9b::550d:93a0 and cannot dial a bare IPv4 address at all.
    ;; The control channel is unaffected, which is why login, CWD and MKD all
    ;; work there and only the transfer fails.
    ;;
    ;; So ignore what the server advertises and use the address the control
    ;; connection is already talking to - the same machine either way. Commons-net
    ;; ships this workaround but only applies it when the advertised address looks
    ;; private, and 85.13.147.160 is public, so it has to be asked for.
    (.setPassiveNatWorkaroundStrategy
      client
      (reify org.apache.commons.net.ftp.FTPClient$HostnameResolver
        (resolve [_ _] (.getHostAddress (.getRemoteAddress client)))))
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
  "Store `stream` as `filename` under the post's own image directory. Returns the
  relative path the post's image field wants - the same shape the existing rows
  carry - or throws. Returns nil, loudly, when no credential is configured."
  [^InputStream stream filename post-id]
  (if-not (configured?)
    (tel/log! :error
      (str "FTP is not configured - refusing to upload " (pr-str filename) ". "
           "Set :ftp {:host .. :username .. :password ..} under :apps :blog."))
    (let [dir (post-prefix post-id)
          name (safe-name filename)
          remote (str "/" dir "/" name)
          client (open @*config)]
      (try
        (ensure-dir! client dir)
        (when-not (.storeFile client remote stream)
          (let [reply (str/trim (or (.getReplyString client) "no reply"))]
            ;; A transfer that dies part-way leaves what did arrive on the server -
            ;; the first failure here left an 8192-byte stub, one buffer's worth,
            ;; sitting where an image should be. Take it back out. This is the only
            ;; delete in the namespace and it names the exact path just attempted,
            ;; so it cannot become a way to remove anything else.
            (try (.deleteFile client remote) (catch Exception _))
            ;; The reply text goes in the message, not only in ex-data: the caller
            ;; surfaces .getMessage, and "FTP refused the upload" on its own says
            ;; nothing about which of the many ways it can refuse this was.
            (throw (ex-info (str "FTP refused the upload - " reply)
                            {:path remote :reply reply}))))
        (str dir "/" name)
        (finally
          (try (.logout client) (catch Exception _))
          (try (.disconnect client) (catch Exception _)))))))

(defn list-post-files
  "What is in a post's image directory: {:name :size :path} each, newest first.
  Empty when the post has no directory yet, which is the normal case - a post
  that never had an image uploaded has no folder, and that is not an error.

  Read-only, and scoped exactly like upload!: one directory, named by a constant
  prefix and an integer. It exists so an image uploaded and then unlinked from
  the post is still findable, rather than being invisible litter on the webspace."
  [post-id]
  (when (configured?)
    (let [dir (post-prefix post-id)
          client (open @*config)]
      (try
        (let [entries (or (seq (.mlistDir client (str "/" dir)))
                          (seq (.listFiles client (str "/" dir))))]
          (->> entries
               (remove #(#{"." ".."} (.getName ^FTPFile %)))
               (filter #(.isFile ^FTPFile %))
               (map (fn [^FTPFile f]
                      {:name (.getName f)
                       :size (.getSize f)
                       :modified (some-> (.getTimestamp f) .getTimeInMillis)
                       :path (str dir "/" (.getName f))}))
               (sort-by :modified #(compare %2 %1))
               vec))
        (catch Exception _
          ;; A directory that is not there lists as an error on some servers and
          ;; as nothing on others. Neither is worth surfacing: the answer to
          ;; "what has this post got?" is then "nothing".
          [])
        (finally
          (try (.logout client) (catch Exception _))
          (try (.disconnect client) (catch Exception _)))))))
