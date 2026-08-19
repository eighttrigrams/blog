(ns et.blog.handler.notes
  "The Notes box. A Note arrives either remotely, through POST /api/notes, or
  straight from the page; either way the owner reads and edits it here behind the
  login. Notes are the owner's own material and never public."
  (:require [et.blog.handler.common :as c]
            [et.blog.db :as db]
            [et.blog.views :as views]
            [clojure.string :as str]))

(defn create-note-api-handler
  "POST /api/notes — deliver a Note into the Notes box. Body {:text, and
  optionally :source}; text is required and is the whole Note, newlines and all.
  Takes either a notes user's bearer token or the owner's login cookie, and is
  the only route on the API that either authorises: 401 without one. Nothing else
  about blog is callable this way, and the Note itself is never public."
  [req]
  (let [author (or (c/notes-user req) (when (c/logged-in? req) "admin"))
        {:keys [text source]} (:body req)]
    (cond
      (nil? author)
      {:status 401 :body {:error "Unauthorized"}}

      (or (not (string? text)) (str/blank? text))
      {:status 400 :body {:error "text is required"}}

      :else
      (do (db/create-note! (c/ensure-ds)
            {:text (str/trim text)
             :source (when (string? source) (str/trim source))})
          {:status 201 :body {:created true}}))))

(defn- page-data [opts]
  (merge {:logged-in? true
          :notes (db/list-notes (c/ensure-ds))}
         opts))

(defn notes-page-handler [req]
  (c/require-login req
    (fn [_]
      (c/html-response 200 (views/notes-page (page-data {}))))))

(defn create-note-handler [req]
  (c/require-login req
    (fn [req]
      (let [text (str/trim (or (get-in req [:form-params "text"]) ""))]
        (if (str/blank? text)
          (c/html-response 400
            (views/notes-page (page-data {:error "A Note needs some text."})))
          (do (db/create-note! (c/ensure-ds) {:text text :source "ui"})
              (c/redirect "/notes")))))))

(defn- with-note [req handler]
  (c/require-login req
    (fn [req]
      (let [id (try (Integer/parseInt (get-in req [:params :id]))
                    (catch NumberFormatException _ nil))
            note (when id (db/get-note (c/ensure-ds) id))]
        (if note
          (handler note)
          (c/html-response 404 (views/not-found-page {:logged-in? true})))))))

(defn edit-note-handler [req]
  (with-note req
    (fn [note]
      (c/html-response 200 (views/edit-note-page {:logged-in? true :note note})))))

(defn update-note-handler [req]
  (with-note req
    (fn [note]
      (let [text (str/trim (or (get-in req [:form-params "text"]) ""))
            ;; An inline save — the click away from a Note being edited in the
            ;; box — posts this same form over fetch and asks for an envelope a
            ;; fetch can read instead of a page to navigate to. Blog already does
            ;; this for Zen's in-between save on an Article, under the same name.
            ;; It changes the response only: the validation and the write below
            ;; are the very ones the form post goes through.
            no-redirect? (some? (get-in req [:form-params "no-redirect"]))]
        (if (str/blank? text)
          (if no-redirect?
            (c/text-response 400 "A Note needs some text.")
            (c/html-response 400
              (views/edit-note-page {:logged-in? true
                                     :note note
                                     :error "A Note needs some text."})))
          (do (db/update-note! (c/ensure-ds) (:id note) {:text text})
              (if no-redirect?
                ;; The Note rendered, so the block that was being edited goes
                ;; back to being read without a reload. Markdown is rendered
                ;; server-side everywhere here, and this is that renderer
                ;; answering — the browser only puts the answer back in place.
                (c/html-response 200 (views/note-text-fragment text))
                (c/redirect "/notes"))))))))

(defn delete-note-handler [req]
  (with-note req
    (fn [note]
      (db/delete-note! (c/ensure-ds) (:id note))
      (c/redirect "/notes"))))
