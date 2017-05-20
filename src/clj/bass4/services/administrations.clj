(ns bass4.services.administrations
  (:require [bass4.db.core :as db]
            [clj-time.core :as t]
            [bass4.utils :refer [key-map-list]]
            [clj-time.coerce]
            [bass4.services.bass :refer [create-bass-objects-without-parent!]]))


;; ------------------------------
;; CREATE MISSING ADMINISTRATIONS
;; ------------------------------

(defn- create-administrations-objects!
  [user-id missing-administrations]
  (create-bass-objects-without-parent!
    "cParticipantAdministration"
    "Administrations"
    (count missing-administrations)))

(defn- delete-administration!
  [administration-id]
  (db/delete-object-from-objectlist! {:object-id administration-id})
  (db/delete-participant-administration! {:administration-id administration-id}))

(defn- update-created-administrations!
  [user-id new-object-ids missing-administrations]
  ;; mapv because not lazy
  (mapv (fn [administration-id {:keys [assessment-index assessment-id]}]
          (try
            ;; Try to update the placeholder with the assessment and index
            (db/update-new-participant-administration!
              {:administration-id administration-id :user-id user-id :assessment-id assessment-id :assessment-index assessment-index})
            (db/update-objectlist-parent! {:object-id administration-id :parent-id user-id})
            administration-id
            ;; If that fails, then delete the placeholder and return instead the
            ;; duplicate administration's id.
            (catch Exception e
              (delete-administration! administration-id)
              (:administration-id (db/get-administration-by-assessment-and-index {:user-id user-id :assessment-id assessment-id :assessment-index assessment-index})))))
        new-object-ids
        missing-administrations))

(defn- create-missing-administrations!
  [user-id missing-administrations]
  (let [new-object-ids
        (update-created-administrations!
          user-id
          (create-administrations-objects! user-id missing-administrations)
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

(defn- get-missing-administrations
  [matching-administrations]
  (map
    #(select-keys % [:assessment-id :assessment-index])
    (filter #(nil? (:participant-administration-id %)) matching-administrations)))

(defn- add-missing-administrations
  [matching-administrations user-id]
  (let [missing-administrations (get-missing-administrations matching-administrations)]
    (if (> (count missing-administrations) 0)
      (insert-new-into-old (create-missing-administrations! user-id missing-administrations) matching-administrations)
      matching-administrations)))

;; ------------------------
;;    XXXX
;; ------------------------



(defn- get-user-administrations
  [user-id]
  (let [group-id (:group-id (db/get-user-group {:user-id user-id}))
        assessment-series-id (:assessment-series-id (db/get-user-assessment-series {:user-id user-id}))
        assessments (db/get-assessment-series-assessments {:assessment-series-id assessment-series-id})
        administrations (db/get-user-administrations {:user-id user-id :group-id group-id :assessment-series-id assessment-series-id})]
    {:administrations (group-by #(:assessment-id %) administrations) :assessments (key-map-list assessments :assessment-id)}))


(defn- get-time-limit [{:keys [time-limit is-record repetition-interval repetition-type]}]
  (when
    (or (> time-limit 0) (and (zero? is-record) (= repetition-type "INTERVAL")))
    (apply min (filter (complement zero?) [time-limit repetition-interval]))))


(defn- get-activation-date [administration assessment]
  (t/plus (clj-time.coerce/from-sql-date
            (if (= (:scope assessment) 0)
              (:participant-activation-date administration)
              (:group-activation-date administration))) (t/hours (:activation-hour assessment))))


(defn- check-next-status [{:keys [repetition-type]} next-administration-status]
  (if (nil? next-administration-status)
    false
    (and (= repetition-type "MANUAL")
         (and (not= next-administration-status "AS_NO_DATE") (not= next-administration-status "AS_INACTIVE")))))


(defn- get-administration-status [administration next-administration-status assessment]
  {:assessment-id (:assessment-id assessment)
   :assessment-index (:assessment-index administration)
   :is-record (:is-record assessment)
   :assessment-name (:assessment-name assessment)
   :clinician-rated (:clinician-rated assessment)
   :status (cond
             (= (:participant-administration-id administration) (:group-administration-id administration) nil) "AS_ALL_MISSING"
             (and (= (:scope assessment) 0) (nil? (:participant-administration-id administration))) "AS_OWN_MISSING"
             (and (= (:scope assessment) 1) (nil? (:group-administration-id administration))) "AS_GROUP_MISSING"
             (> (:date-completed administration) 0) "AS_COMPLETED"
             (> (:assessment-index administration) (:repetitions assessment)) "AS_SUPERFLUOUS"
             (zero? (:active administration)) "AS_INACTIVE"
             (check-next-status assessment next-administration-status) "AS_DATE_PASSED"
             :else (let [activation-date (get-activation-date administration assessment)
                         time-limit (get-time-limit assessment)]
                     (cond
                       (t/equal? (t/epoch) activation-date) "AS_NO_DATE"
                       (t/before? (t/now) activation-date) "AS_WAITING"
                       (and (some? time-limit) (t/after? (t/now) (t/plus activation-date (t/days time-limit)))) "AS_DATE_PASSED"
                       :else "AS_PENDING")))})

(defn- get-assessment-statuses [administrations assessments]
  (when (seq administrations)
    (let [next-administrations (get-assessment-statuses (rest administrations) assessments)
          current-administration (first administrations)
          current-assessment (get assessments (:assessment-id current-administration))]
      (cons (get-administration-status current-administration (last (first next-administrations)) current-assessment) next-administrations))))

(defn- process-administrations [administrations assessments]
  (flatten (map #(get-assessment-statuses % assessments)
                (map #(sort-by :assessment-index (val %)) administrations))))

(defn- filter-pending [assessment-statuses]
  (filter #(and
             (= (:status %) "AS_PENDING")
             (zero? (:is-record %))
             (zero? (:clinician-rated %)))
          assessment-statuses))

(defn matching-administration
  [assessment-index administrations]
  (filter #(= (:assessment-index %) assessment-index) administrations))


(defn- collect-assessment-administrations
  [pending-assessments administrations]

  ;; This function assumes that there is a row for all missing administrations
  ;; with a nil-value for participant-administration-id. I can't imagine that
  ;; there could be an empty row.
  ;; If such a case could exist, the map would have to be enclosed in a map
  ;; that adds the missing assessment-id and assessment-index to the result.
  ;; Remove flatten
  ;;
  ;; (map
  ;;  #(merge (first %1) %2)
  ;;  X
  ;;  (select-keys pending-assessments [:assessment-id :assessment-index]))

  (flatten
    (map
      (fn [{:keys [assessment-id assessment-index]}]
        (matching-administration
          assessment-index
          (get administrations assessment-id)))
      pending-assessments)))

(defn- add-instruments [administrations]
  (map #(assoc % :instruments (db/get-assessment-instruments {:assessment-id (:assessment-id %)})) administrations))

(defn- get-pending-administrations [user-id]
  (let
    [{:keys [administrations assessments]} (get-user-administrations user-id)
     pending-administrations (-> administrations
                                 (process-administrations assessments)
                                 filter-pending
                                 (collect-assessment-administrations administrations)
                                 (add-missing-administrations user-id))]
    {:administrations pending-administrations
     :assessments assessments}))

(defn get-administrations [user-id]
  (let [administrations (get-pending-administrations user-id)]
    (map #(select-keys % [:assessment-id :participant-administration-id :assessment-index :instruments])
         (add-instruments (:administrations administrations)))))
