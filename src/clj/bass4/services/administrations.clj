(ns bass4.services.administrations
  (:require [bass4.db.core :as db]
            [clj-time.core :as t]
            [bass4.utils :refer [key-map-list map-map]]
            [clj-time.coerce]
            [bass4.services.bass :refer [create-bass-objects-without-parent!]]
            [taoensso.nippy :as nippy]))


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
  [user-id matching-administrations]
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
  (when-let [activation-date (if (= (:scope assessment) 0)
                               (:participant-activation-date administration)
                               (:group-activation-date administration))]
    (t/plus (clj-time.coerce/from-sql-date
              activation-date) (t/hours (:activation-hour assessment)))))

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
                       (nil? activation-date) "AS_NO_DATE"
                       (t/before? (t/now) activation-date) "AS_WAITING"
                       (and (some? time-limit) (t/after? (t/now) (t/plus activation-date (t/days time-limit)))) "AS_DATE_PASSED"
                       :else "AS_PENDING")))})

(defn- get-assessment-statuses [administrations assessments]
  (when (seq administrations)
    (let [next-administrations (get-assessment-statuses (rest administrations) assessments)
          current-administration (first administrations)
          current-assessment (get assessments (:assessment-id current-administration))]
      (cons (get-administration-status current-administration (last (first next-administrations)) current-assessment) next-administrations))))


(defn- filter-pending-assessments [assessment-statuses]
  (filter #(and
             (= (:status %) "AS_PENDING")
             (zero? (:is-record %))
             (zero? (:clinician-rated %)))
          assessment-statuses))

(defn matching-administration
  [assessment-index administrations]
  (filter #(= (:assessment-index %) assessment-index) administrations))


(defn- collect-assessment-administrations
  [administrations pending-assessments]

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

;; TODO: Add "komplettera mätning" instruments
(defn- add-instruments [administrations]
  (map #(assoc % :instruments (map :instrument-id (db/get-assessment-instruments {:assessment-id (:assessment-id %)}))) administrations))


(defn merge-batches
  [coll val]
  (if (and (seq coll) (= (:allow-swallow val) 1))
    (concat (butlast coll) (list (concat (last coll) (list val))))
    (concat coll (list (list val)))))


;; ------------------------
;;     ROUNDS CREATION
;; ------------------------


(defn batch-texts
  [text-name]
  (fn
    [batch]
    (pr-str (remove
                    #(some (partial = %) [nil ""])
                    (map-indexed
                      (fn [idx assessment] (when (or (= idx 0) (= (:show-texts-if-swallowed assessment))) (get assessment text-name)))
                      batch)))))

(defn assessment-instruments
  [assessment]
  (if (= (:shuffle-instruments assessment) 1) (shuffle (:instruments assessment)) (:instruments assessment)))

(defn batch-instruments
  [batch]
  (flatten
    (map (fn [assessment]
           (map #(do {:administration-id (:participant-administration-id assessment) :instrument-id %})
                (assessment-instruments assessment)))
         batch)))

(defn batch-steps
  [idx batch]
  (let [welcome {:text ((batch-texts :welcome-text) batch)}
        thank-you {:text ((batch-texts :thank-you-text) batch)}
        instruments (batch-instruments batch)]
    (map #(merge {:batch-id idx} %) (flatten (list welcome instruments thank-you)))))

(defn step-row
  [user-id]
  (fn [idx step]
    (merge
      {:time              (clj-time.coerce/to-sql-date (t/now))
       :user-id           user-id
       :batch-id          nil
       :step              idx
       :text              nil
       :instrument-id     nil
       :administration-id nil}
      step)))

;; TODO: Fix concurrency with insert
(defn save-round!
  [round]
  (let [round-id (or (:round-id (db/get-new-round-id)) 0)]
    (db/insert-assessment-round! {:rows (map #(cons round-id %) (map vals round))})))

(defn- get-pending-assessments [user-id]
  (let
    ;; NOTE that administrations is a map of lists
    ;; administrations within one assessment battery
    [{:keys [administrations assessments]} (get-user-administrations user-id)
     pending-assessments (->> (vals administrations)
                              ;; Sort administrations by their assessment-index
                              (map #(sort-by :assessment-index %))
                              ;; Return assessment (!) statuses
                              (map #(get-assessment-statuses % assessments))
                              ;; Remove lists within list
                              flatten
                              ;; Keep the assessments that are AS_PENDING
                              (filter-pending-assessments)
                              ;; Find corresponding administrations
                              (collect-assessment-administrations administrations)
                              ;; Add any missing administrations
                              (add-missing-administrations user-id)
                              ;; Merge assessment and administration info into one map
                              (map #(merge % (get assessments (:assessment-id %))))
                              ;; Sort assessments by priority
                              (sort-by :priority)
                              add-instruments
                              ;; Create rounds of merged assessments, according to "allow-swallow" setting
                              (reduce merge-batches ())
                              ;; Create round steps
                              (map-indexed batch-steps)
                              ;; Remove lists within batches
                              flatten
                              ;; Create step db rows
                              (map-indexed (step-row user-id))
                              ;; Finally (save-round!)
                              )]
    pending-assessments))



(def x (get-pending-assessments 535759))
;(def administrations (:administrations y))
;(def assessments (:assessments y))
;(def x administrations)
;(def x (vals x))
;(def x (mapv #(sort-by :assessment-index %) x))
;(def administration (first (nth x 4)))
;(def assessment (get assessments (:assessment-id administration)))
;(def activation-date (get-activation-date administration assessment))