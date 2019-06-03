(ns bass4.assessment.create-missing
  (:require [bass4.services.bass :as bass]
            [bass4.db.core :as db]
            [clojure.tools.logging :as log]))

(defn- create-administrations-objects!
  [missing-administrations]
  (bass/create-bass-objects-without-parent!
    "cParticipantAdministration"
    "Administrations"
    (count missing-administrations)))

(defn- delete-administration!
  [administration-id]
  (log/info "Deleting surplus administrations " administration-id)
  (db/delete-object-from-objectlist! {:object-id administration-id})
  (db/delete-participant-administration! {:administration-id administration-id}))

(defn- update-created-administrations!
  [user-id new-object-ids missing-administrations]
  ;; mapv because not lazy
  (mapv (fn [administration-id {:keys [assessment-index assessment-id]}]
          (try
            ;; Try to update the placeholder with the assessment and index
            (db/update-new-participant-administration!
              {:administration-id administration-id
               :user-id           user-id
               :assessment-id     assessment-id
               :assessment-index  assessment-index})
            (db/update-objectlist-parent!
              {:object-id administration-id
               :parent-id user-id})
            (db/link-property-reverse!
              {:linkee-id     administration-id
               :property-name "Assessment"
               :linker-class  "cParticipantAdministration"})
            administration-id
            ;; If that fails, then delete the placeholder and return instead the
            ;; duplicate administration's id.
            (catch Exception e
              (delete-administration! administration-id)
              (:administration-id (db/get-administration-by-assessment-and-index
                                    {:user-id          user-id
                                     :assessment-id    assessment-id
                                     :assessment-index assessment-index})))))
        new-object-ids
        missing-administrations))

(defn create-missing-administrations!
  "user-id [{:assessment-id 666 :assessment-index 0}]"
  [user-id missing-administrations]
  (let [new-object-ids
        (update-created-administrations!
          user-id
          (create-administrations-objects! missing-administrations)
          missing-administrations)]
    (map #(assoc %1 :participant-administration-id %2) missing-administrations new-object-ids)))


(defn- insert-new-into-old
  [new-administrations old-administrations]
  (map
    (fn [old]
      (if (nil? (:participant-administration-id old))
        (conj old (first (filter
                           #(and
                              (= (:assessment-id old) (:assessment-id %1))
                              (= (:assessment-index old) (:assessment-index %1)))
                           new-administrations)))
        old)
      )
    old-administrations))

#_(defn- get-missing-administrations
    [matching-administrations]
    (map
      #(select-keys % [:assessment-id :assessment-index])
      (filter #(nil? (:participant-administration-id %)) matching-administrations)))

(defn add-missing-administrations!
  [user-id matching-administrations]
  (let [missing-administrations (remove :participant-administration-id matching-administrations)]
    (if (> (count missing-administrations) 0)
      (insert-new-into-old
        (create-missing-administrations! user-id missing-administrations)
        matching-administrations)
      matching-administrations)))
