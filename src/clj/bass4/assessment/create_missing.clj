(ns bass4.assessment.create-missing
  (:require [bass4.db.core :as db]
            [bass4.db.orm-classes :as orm]
            [clojure.core.async :refer [put!]]
            [clojure.tools.logging :as log]))

(def ^:dynamic *create-count-chan* nil)

(defn- create-administrations-objects!
  [db missing-administrations]
  (let [created-ids (orm/create-bass-objects-without-parent*!
                      db
                      "cParticipantAdministration"
                      "Administrations"
                      (count missing-administrations))]
    (when *create-count-chan*
      (put! *create-count-chan* (count created-ids)))
    created-ids))

(defn- delete-administration!
  [db administration-id]
  (log/info "Deleting surplus administrations " administration-id)
  (db/delete-object-from-objectlist! db {:object-id administration-id})
  (db/delete-participant-administration! db {:administration-id administration-id}))

(defn- update-created-administrations!
  [db new-object-ids missing-administrations]
  ;; mapv because not lazy
  (mapv (fn [new-administration-id {:keys [assessment-index assessment-id user-id]}]
          (try
            ;; Try to update the placeholder with the assessment and index
            (db/update-new-participant-administration!
              db
              {:administration-id new-administration-id
               :user-id           user-id
               :assessment-id     assessment-id
               :assessment-index  assessment-index})
            (db/update-objectlist-parent!
              db
              {:object-id new-administration-id
               :parent-id user-id})
            (db/link-property-reverse!
              db
              {:linkee-id     new-administration-id
               :property-name "Assessment"
               :linker-class  "cParticipantAdministration"})
            new-administration-id
            ;; If that fails, then delete the placeholder and return instead the
            ;; duplicate administration's id.
            (catch Exception e
              (delete-administration! db new-administration-id)
              (:administration-id (db/get-administration-by-assessment-and-index
                                    db
                                    {:user-id          user-id
                                     :assessment-id    assessment-id
                                     :assessment-index assessment-index})))))
        new-object-ids
        missing-administrations))

(defn create-missing-administrations*!
  "Requires :user-id key to be present for all administrations"
  [db missing-administrations]
  (let [new-object-ids (update-created-administrations! db
                                                        (create-administrations-objects! db missing-administrations)
                                                        missing-administrations)]
    (map #(assoc %1 :participant-administration-id %2) missing-administrations new-object-ids)))

(defn create-missing-administrations!
  "Requires :user-id key to be present for all administrations"
  [missing-administrations]
  (create-missing-administrations*! db/*db* missing-administrations))


(defn- insert-new-into-old
  [new-administrations old-administrations]
  (map
    (fn [old]
      (if (nil? (:participant-administration-id old))
        (conj old (first (filter
                           #(and
                              (= (:user-id old) (:user-id %1))
                              (= (:assessment-id old) (:assessment-id %1))
                              (= (:assessment-index old) (:assessment-index %1)))
                           new-administrations)))
        old)
      )
    old-administrations))

(defn add-missing-administrations!
  "Requires :user-id key to be present for all administrations"
  [db administrations]
  (let [missing-administrations (remove :participant-administration-id administrations)]
    (if (< 0 (count missing-administrations))
      (do
        (insert-new-into-old
          (create-missing-administrations*! db missing-administrations)
          administrations))
      administrations)))
