(ns bass4.services.instrument-answers
  (:require [bass4.db.core :as db]
            [clj-time.core :as t]
            [bass4.utils :refer [key-map-list map-map indices fnil+]]
            [bass4.php_clj.core :refer [php->clj clj->php]]
            [bass4.services.bass :refer [create-bass-objects-without-parent!]]
            [clojure.tools.logging :as log]))

;; ------------------------------
;;    CREATE MISSING ANSWERS
;; ------------------------------

(defn- create-answers-object!
  []
  (create-bass-objects-without-parent!
    "cInstrumentAnswers"
    "InstrumentAnswers"
    1))

(defn- delete-answers!
  [answers-id]
  (log/info "Deleting surplus answers " answers-id)
  (db/delete-object-from-objectlist! {:object-id answers-id})
  (db/delete-instrument-answers! {:answers-id answers-id}))

(defn- update-created-answers!
  [answers-id administration-id instrument-id]
  (try
    (db/create-instrument-answers! {:answers-id answers-id :administration-id administration-id :instrument-id instrument-id})
    (db/update-objectlist-parent! {:object-id answers-id :parent-id administration-id})
    answers-id
    (catch Exception e (delete-answers! answers-id)
                       (:answers-id (db/get-instrument-answers-by-administration {:administration-id administration-id :instrument-id instrument-id})))))

(defn- create-answers!
  [administration-id instrument-id]
  (-> (create-answers-object!)
      (first)
      (update-created-answers! administration-id instrument-id)))

(defn- instrument-answers-id
  [administration-id instrument-id]
  (let [answers (db/get-instrument-answers-by-administration {:administration-id administration-id :instrument-id instrument-id})]
    (if-not (seq answers)
      (create-answers! administration-id instrument-id)
      (:answers-id answers))))


;; ------------------------------
;;         SAVE ANSWERS
;; ------------------------------

(defn save-answers!
  [answers-id {:keys [items specifications sums]}]
  (db/save-instrument-answers!
    {:answers-id answers-id
     :items (clj->php items)
     :specifications (clj->php specifications)
     :sums (clj->php sums)}))

(defn save-administrations-answers!
  [administration-ids instrument-id answers-map]
  #_(if (> (count administration-ids) 1))
  (mapv (fn [adm-id] (let [answers-id (instrument-answers-id adm-id instrument-id)]
                  (save-answers!
                    answers-id
                    answers-map))) administration-ids))

(defn get-answers
  [administration-id instrument-id]
  (let [answers (db/get-instrument-answers-by-administration {:administration-id administration-id :instrument-id instrument-id})]
    (merge answers
           {:items (php->clj (:items answers))
            :specifications (php->clj (:specifications answers))
            :sums (php->clj (:sums answers))})))