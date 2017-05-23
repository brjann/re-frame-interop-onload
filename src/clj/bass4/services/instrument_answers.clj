(ns bass4.services.instrument-answers
  (:require [bass4.db.core :as db]
            [clj-time.core :as t]
            [bass4.utils :refer [key-map-list map-map indices fnil+]]
            [clj-time.coerce]
            [bass4.services.bass :refer [create-bass-objects-without-parent!]]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]))

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
    (catch Exception e nil
                       (delete-answers! answers-id)
                       (:answers-id (db/get-instrument-answers {:administration-id administration-id :instrument-id instrument-id})))))

(defn- create-answers!
  [administration-id instrument-id]
  (-> (create-answers-object!)
      (first)
      (update-created-answers! administration-id instrument-id)))

(defn instrument-answers
  [administration-id instrument-id]
  (let [answers (db/get-instrument-answers {:administration-id administration-id :instrument-id instrument-id})]
    (if-not (seq answers)
      (create-answers! administration-id instrument-id)
      (:answers-id answers))))