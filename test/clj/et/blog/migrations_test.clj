(ns et.blog.migrations-test
  "Migrations are the one thing here that cannot be redone later: they run once
   against real data and then the old shape is gone. So they are tested against a
   database built the way production's was — every earlier migration applied,
   prod-shaped rows inserted, and only then the migration under test. A migration
   proved on an empty table has proved nothing."
  (:require [clojure.test :refer [deftest testing is]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [ragtime.core :as ragtime]
            [ragtime.next-jdbc :as ragtime-jdbc]))

(def ^:private all-migrations
  (delay (ragtime-jdbc/load-resources "migrations/net/et/blog")))

(defn- migration [id]
  (or (first (filter #(= id (:id %)) @all-migrations))
      (throw (ex-info "No such migration"
                      {:id id :known (mapv :id @all-migrations)}))))

(defn- migrate-up-to!
  "Apply every migration before `id`, leaving the database in the shape `id` will
   find. Naming the one under test rather than the eighteen before it keeps the
   fixture readable; the lookup still throws on an unknown name, so a typo fails
   as `No such migration` rather than as a missing table ten assertions later."
  [store id]
  (doseq [m (take-while #(not= id (:id %)) @all-migrations)]
    (ragtime/migrate store m))
  (migration id))

(defn- migrate! [store id] (ragtime/migrate store (migration id)))
(defn- rollback! [store id] (ragtime/rollback store (migration id)))

(defn- fresh-db
  "A connection to a private in-memory database with no migrations applied. Each
   caller passes its own `nm` — `file::memory:?cache=shared` is one database for
   the whole JVM, so two tests sharing a name would share a schema."
  [nm]
  (jdbc/get-connection
    (jdbc/get-datasource {:dbtype "sqlite"
                          :dbname (str "file:" nm "?mode=memory&cache=shared")})))

(defn- q [conn sql]
  (jdbc/execute! conn [sql] {:builder-fn rs/as-unqualified-lower-maps}))

(def ^:private collapse "020-collapse-note-title-and-description")

;; The Notes box as it stood: one delivered over the API and edited since, one
;; typed into the page, one from Telegram with nothing under its heading — which
;; is the shape the `b` prefix always produced, and the case the collapse must not
;; leave a trailing blank line on.
(def ^:private prod-notes
  [{:id 1 :title "Delivered over the API, then edited here"
    :description "The description is a first-class editable field."
    :source "telegram" :done 1}
   {:id 2 :title "Typed straight into the box"
    :description "Because the API is only one way a Note arrives."
    :source "ui" :done 0}
   {:id 3 :title "remember the milk" :description "" :source "telegram" :done 0}
   {:id 4 :title "Whitespace is not a description" :description "   "
    :source "telegram" :done 0}])

(defn- seed-old-shape!
  "Insert Notes in the pre-020 shape, plus a notes user the migration has no
   business touching."
  [conn]
  (doseq [{:keys [id title description source done]} prod-notes]
    (jdbc/execute! conn
      ["INSERT INTO notes (id, title, description, source, done) VALUES (?,?,?,?,?)"
       id title description source done]))
  (jdbc/execute! conn
    ["INSERT INTO notes_users (name, password_hash) VALUES (?, ?)"
     "notes-user" "bcrypt+sha512$aaa"]))

(defn- note-texts [conn]
  (map :text (q conn "SELECT text FROM notes ORDER BY id")))

(deftest a-notes-title-and-description-become-one-text
  (with-open [conn (fresh-db "blog-mig020-up")]
    (let [store (ragtime-jdbc/sql-database (jdbc/with-options conn {}))]
      (migrate-up-to! store collapse)
      (seed-old-shape! conn)
      (let [rest-of-the-row (q conn "SELECT id, source, done, created_at, modified_at FROM notes ORDER BY id")
            users-before (q conn "SELECT id, name, password_hash, revoked_at, created_at FROM notes_users ORDER BY id")]

        (migrate! store collapse)

        (testing "the two fields are one text, title first and a blank line between"
          (is (= ["Delivered over the API, then edited here\n\nThe description is a first-class editable field."
                  "Typed straight into the box\n\nBecause the API is only one way a Note arrives."
                  "remember the milk"
                  "Whitespace is not a description"]
                 (note-texts conn))))

        (testing "- a Note that had no description keeps its one line, with no
                  trailing blank line bolted on"
          (is (= "remember the milk" (nth (note-texts conn) 2)))
          (is (= "Whitespace is not a description" (nth (note-texts conn) 3))
              "a description of nothing but spaces counts as none"))

        (testing "everything else about the row came through verbatim"
          (is (= rest-of-the-row
                 (q conn "SELECT id, source, done, created_at, modified_at FROM notes ORDER BY id"))))

        (testing "the old columns are gone, not merely emptied"
          (is (= #{"id" "text" "source" "done" "created_at" "modified_at"}
                 (set (map :name (q conn "PRAGMA table_info(notes)"))))))

        (testing "notes users are none of this migration's business"
          (is (= users-before
                 (q conn "SELECT id, name, password_hash, revoked_at, created_at FROM notes_users ORDER BY id"))))

        (testing "a Note written afterwards needs nothing but its text"
          (jdbc/execute! conn ["INSERT INTO notes (text, source) VALUES (?, ?)" "Fresh" "telegram"])
          (is (= "Fresh" (:text (first (q conn "SELECT text FROM notes WHERE source = 'telegram' ORDER BY id DESC LIMIT 1"))))))))))

;; ADD COLUMN and DROP COLUMN are in-place edits rather than a rebuild, so the
;; id counter cannot be lost here — and this is the test that says so if one of
;; them is ever turned into a copy-into-a-scratch-table.
(deftest the-note-id-counter-survives-the-collapse
  (with-open [conn (fresh-db "blog-mig020-seq")]
    (let [store (ragtime-jdbc/sql-database (jdbc/with-options conn {}))]
      (migrate-up-to! store collapse)
      (seed-old-shape! conn)
      ;; a fifth Note, then deleted: MAX(id) is 4 again but the high-water mark is
      ;; 5, which is the state any real box that has ever lost its newest Note is in
      (jdbc/execute! conn ["INSERT INTO notes (title, source) VALUES ('gone', 'ui')"])
      (let [gone (:id (first (q conn "SELECT id FROM notes WHERE title = 'gone'")))]
        (jdbc/execute! conn ["DELETE FROM notes WHERE title = 'gone'"])

        (migrate! store collapse)

        (testing "the next Note gets a fresh id, never the deleted one's"
          (jdbc/execute! conn ["INSERT INTO notes (text, source) VALUES ('new', 'ui')"])
          (is (= (inc gone)
                 (:id (first (q conn "SELECT id FROM notes WHERE text = 'new'"))))))))))

(deftest rollback-splits-the-first-line-back-off
  (with-open [conn (fresh-db "blog-mig020-down")]
    (let [store (ragtime-jdbc/sql-database (jdbc/with-options conn {}))]
      (migrate-up-to! store collapse)
      (seed-old-shape! conn)
      (let [notes-before (q conn "SELECT id, title, description, source, done, created_at, modified_at FROM notes ORDER BY id")]
        (migrate! store collapse)

        (rollback! store collapse)

        (testing "the old shape is back"
          (is (= #{"id" "title" "description" "source" "done" "created_at" "modified_at"}
                 (set (map :name (q conn "PRAGMA table_info(notes)"))))))

        (testing "and every Note the up touched round-trips, but for a description
                  that was only whitespace — the up read that as none, so the down
                  has nothing to put back"
          (is (= (map #(if (clojure.string/blank? (:description %))
                         (assoc % :description "")
                         %)
                      notes-before)
                 (q conn "SELECT id, title, description, source, done, created_at, modified_at FROM notes ORDER BY id"))))

        (testing "a Note written after the up, with no title-shaped first line,
                  comes back split at its first newline — the price of collapsing,
                  pinned here rather than left to be discovered"
          (migrate! store collapse)
          (jdbc/execute! conn ["INSERT INTO notes (text, source) VALUES (?, ?)"
                               "just a thought\nthat ran on" "telegram"])
          (rollback! store collapse)
          (let [row (first (q conn "SELECT title, description FROM notes ORDER BY id DESC LIMIT 1"))]
            (is (= "just a thought" (:title row)))
            (is (= "that ran on" (:description row)))))))))
