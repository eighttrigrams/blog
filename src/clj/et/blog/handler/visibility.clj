(ns et.blog.handler.visibility
  "What an anonymous visitor may read, as opposed to what the owner may. The HTML
  handlers and the read-only JSON API both ask their questions here, so the two
  cannot come to different answers about a draft or a deleted article."
  (:require [et.blog.db :as db]
            [et.blog.handler.common :as c]))

(defn visible-article
  "The article a request asks for: its current version, one numbered version, or
  its state as of a timestamp. Under `pub?` — nobody signed in — version 0 is a
  draft that is not an article yet, and a deleted one is gone."
  [ds article-id {:keys [pub? version as-of]}]
  (let [raw (cond
              as-of (db/get-article-version ds article-id as-of {})
              version (when-not (and pub? (zero? version))
                        (db/get-article-by-version ds article-id version {}))
              :else (db/get-article ds article-id {:published-only? pub?}))]
    (when-not (and pub? raw (zero? (or (:version raw) 0)))
      raw)))

(defn visible-versions
  "Every version row of an article the reader may see, newest first. An edit
  within a version adds a row and keeps the version number, so a version can be
  here more than once."
  [ds article-id {:keys [pub?]}]
  (db/get-article-versions ds article-id {:published-only? pub?}))

(defn with-replies
  "The comments with their replies attached, in one query for the whole thread."
  [ds comments]
  (let [replies-by-comment (when (seq comments)
                             (group-by :comment_id
                               (db/get-replies-for-comments ds (map :id comments))))]
    (mapv #(assoc % :replies (get replies-by-comment (:id %))) comments)))

(defn listed-articles
  "The articles a listing shows: each at its newest version, carrying the version
  and date of its latest announcement, latest announcement first. Article 36 is
  the site's landing page rather than an entry in the list, so a visitor is not
  shown it twice; the owner, who navigates by the list, still gets it."
  [ds {:keys [pub?]}]
  (let [articles (db/list-articles ds {:published-only? pub?})
        post-dates (db/get-articles-latest-post-dates ds (mapv :article_id articles))]
    (->> articles
         (mapv #(let [pd (get post-dates (:article_id %))]
                  (-> %
                      c/resolve-preview-image
                      (assoc :latest-version (:article_version pd))
                      (assoc :latest-published-at (:published_at pd)))))
         (sort-by :latest-published-at #(compare %2 %1))
         (remove #(and pub? (= 36 (:article_id %))))
         vec)))
