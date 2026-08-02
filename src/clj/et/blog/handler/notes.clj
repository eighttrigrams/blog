(ns et.blog.handler.notes
  "The Notes box. A Note arrives either remotely, through POST /api/notes, or
  straight from the page; either way the owner reads and edits it here behind the
  login. Notes are the owner's own material and never public."
  (:require [et.blog.handler.common :as c]
            [et.blog.db :as db]
            [et.blog.views :as views]
            [clojure.string :as str]))

(defn create-note-api-handler
  "POST /api/notes — deliver a Note into the Notes box. Body {:title, and
  optionally :description and :source}; title is required. Takes either a notes
  user's bearer token or the owner's login cookie, and is the only route on the
  API that either authorises: 401 without one. Nothing else about blog is
  callable this way, and the Note itself is never public."
  [req]
  (let [author (or (c/notes-user req) (when (c/logged-in? req) "admin"))
        {:keys [title description source]} (:body req)]
    (cond
      (nil? author)
      {:status 401 :body {:error "Unauthorized"}}

      (or (not (string? title)) (str/blank? title))
      {:status 400 :body {:error "title is required"}}

      :else
      (do (db/create-note! (c/ensure-ds)
            {:title (str/trim title)
             :description (when (string? description) description)
             :source (when (string? source) (str/trim source))})
          {:status 201 :body {:created true}}))))

(defn- page-data [opts]
  (merge {:logged-in? true
          :notes (db/list-open-notes (c/ensure-ds))}
         opts))

(defn notes-page-handler [req]
  (c/require-login req
    (fn [_]
      (c/html-response 200 (views/notes-page (page-data {}))))))

(defn create-note-handler [req]
  (c/require-login req
    (fn [req]
      (let [title (str/trim (or (get-in req [:form-params "title"]) ""))
            description (or (get-in req [:form-params "description"]) "")]
        (if (str/blank? title)
          (c/html-response 400
            (views/notes-page (page-data {:error "A Note needs a title."})))
          (do (db/create-note! (c/ensure-ds)
                {:title title :description description :source "ui"})
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
      (let [title (str/trim (or (get-in req [:form-params "title"]) ""))
            description (or (get-in req [:form-params "description"]) "")]
        (if (str/blank? title)
          (c/html-response 400
            (views/edit-note-page {:logged-in? true
                                   :note (assoc note :description description)
                                   :error "A Note needs a title."}))
          (do (db/update-note! (c/ensure-ds) (:id note)
                {:title title :description description})
              (c/redirect "/notes")))))))

(defn done-note-handler [req]
  (with-note req
    (fn [note]
      (db/mark-note-done! (c/ensure-ds) (:id note))
      (c/redirect "/notes"))))
