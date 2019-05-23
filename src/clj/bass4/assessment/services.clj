(ns bass4.assessment.services
  (:require [bass4.db.core :as db]
            [clj-time.core :as t]
            [bass4.utils :refer [key-map-list map-map indices fnil+ diff in? select-values]]
            [bass4.services.bass :refer [create-bass-objects-without-parent!]]
            [clojure.tools.logging :as log]))


;; ------------------------------
;; CREATE MISSING ADMINISTRATIONS
;; ------------------------------

(defn- create-administrations-objects!
  [missing-administrations]
  (create-bass-objects-without-parent!
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
              {:administration-id administration-id :user-id user-id :assessment-id assessment-id :assessment-index assessment-index})
            (db/update-objectlist-parent! {:object-id administration-id :parent-id user-id})
            (db/link-property-reverse! {:linkee-id administration-id :property-name "Assessment" :linker-class "cParticipantAdministration"})
            administration-id
            ;; If that fails, then delete the placeholder and return instead the
            ;; duplicate administration's id.
            (catch Exception e
              (delete-administration! administration-id)
              (:administration-id (db/get-administration-by-assessment-and-index {:user-id user-id :assessment-id assessment-id :assessment-index assessment-index})))))
        new-object-ids
        missing-administrations))

(defn create-missing-administrations!
  "user-id
  [{:assessment-id 666 :assessment-index 0}]"
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
;; GET PENDING ASSESSMENTS
;; ------------------------

(defn- user-assessments
  [user-id assessment-series-id]
  (let [assessments (db/get-user-assessments {:assessment-series-id assessment-series-id :user-id user-id})]
    (key-map-list assessments :assessment-id)))

(defn- user-administrations
  [user-id group-id assessment-series-id]
  (let [administrations (db/get-user-administrations {:user-id user-id :group-id group-id :assessment-series-id assessment-series-id})]
    (group-by #(:assessment-id %) administrations)))


(defn- get-time-limit
  [{:keys [time-limit is-record repetition-interval repetition-type]}]
  (when
    (or (> time-limit 0) (and (not is-record) (= repetition-type "INTERVAL")))
    (apply min (filter (complement zero?) [time-limit repetition-interval]))))

(defn- get-activation-date
  [administration assessment]
  (when-let [activation-date (if (= (:scope assessment) 0)
                               (:participant-activation-date administration)
                               (:group-activation-date administration))]
    (t/plus activation-date (t/hours (:activation-hour assessment)))))

(defn- check-next-status
  [{:keys [repetition-type]} next-administration-status]
  (if (nil? next-administration-status)
    false
    (and (= repetition-type "MANUAL")
         (and (not= next-administration-status ::as-no-date) (not= next-administration-status ::as-inactive)))))


(defn- get-administration-status
  [administration next-administration-status assessment]
  {:assessment-id    (:assessment-id assessment)
   :assessment-index (:assessment-index administration)
   :is-record?       (:is-record? assessment)
   :assessment-name  (:assessment-name assessment)
   :clinician-rated? (:clinician-rated? assessment)
   :status           (cond
                       (= (:participant-administration-id administration) (:group-administration-id administration) nil)
                       ::as-all-missing

                       (and (= (:scope assessment) 0) (nil? (:participant-administration-id administration)))
                       ::as-own-missing

                       (and (= (:scope assessment) 1) (nil? (:group-administration-id administration)))
                       ::as-group-missing

                       (> (:date-completed administration) 0)
                       ::as-completed

                       (> (:assessment-index administration) (:repetitions assessment))
                       ::as-superfluous

                       (not (:active? administration))
                       ::as-inactive

                       (check-next-status assessment next-administration-status)
                       ::as-date-passed

                       :else
                       (let [activation-date (get-activation-date administration assessment)
                             time-limit      (get-time-limit assessment)]
                         (cond
                           ;; REMEMBER:
                           ;; activation-date is is UTC time of activation,
                           ;; NOT local time. Thus, it is sufficient to compare
                           ;; to t/now which returns UTC time
                           (nil? activation-date) ::as-no-date

                           (t/before? (t/now) activation-date)
                           ::as-waiting

                           (and (some? time-limit) (t/after? (t/now) (t/plus activation-date (t/days time-limit))))
                           ::as-date-passed

                           :else ::as-ongoing)))})

(defn- get-assessment-statuses
  [administrations assessments]
  (when (seq administrations)
    (let [next-administrations   (get-assessment-statuses (rest administrations) assessments)
          current-administration (first administrations)
          current-assessment     (get assessments (:assessment-id current-administration))]
      (when (nil? current-assessment)
        (throw (Exception. (str "Assessment ID: " (:assessment-id current-administration) " does not exist."))))
      (cons (get-administration-status current-administration (last (first next-administrations)) current-assessment) next-administrations))))


(defn- filter-pending-assessments
  [assessment-statuses]
  (filter #(and
             (= (:status %) ::as-ongoing)
             (not (:is-record? %))
             (not (:clinician-rated? %)))
          assessment-statuses))

(defn- matching-administration
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

(defn- add-instruments [assessments]
  (let [administration-ids     (map :participant-administration-id assessments)
        assessment-instruments (->> {:assessment-ids (map :assessment-id assessments)}
                                    (db/get-assessments-instruments)
                                    (group-by :assessment-id)
                                    (map-map #(map :instrument-id %)))
        completed-instruments  (->> {:administration-ids administration-ids}
                                    (db/get-administration-completed-instruments)
                                    (group-by :administration-id)
                                    (map-map #(map :instrument-id %)))
        additional-instruments (->> {:administration-ids administration-ids}
                                    (db/get-administration-additional-instruments)
                                    (group-by :administration-id)
                                    (map-map #(map :instrument-id %)))]
    (map #(assoc % :instruments (diff
                                  (concat
                                    (get assessment-instruments (:assessment-id %))
                                    (get additional-instruments (:participant-administration-id %)))
                                  (get completed-instruments (:participant-administration-id %))))
         assessments)))

(defn ongoing-assessments
  [user-id]
  (let
    ;; NOTE that administrations is a map of lists
    ;; administrations within one assessment battery
    ;;
    ;; Amazingly enough, this all works even with no pending administrations
    [group-id             (:group-id (db/get-user-group {:user-id user-id}))
     assessment-series-id (:assessment-series-id (db/get-user-assessment-series {:user-id user-id}))
     administrations      (user-administrations user-id group-id assessment-series-id)
     assessments          (user-assessments user-id assessment-series-id)
     pending-assessments  (->> (vals administrations)
                               ;; Sort administrations by their assessment-index
                               (map #(sort-by :assessment-index %))
                               ;; Return assessment (!) statuses
                               (map #(get-assessment-statuses % assessments))
                               ;; Remove lists within list
                               (flatten)
                               ;; Keep the assessments that are AS_PENDING
                               (filter-pending-assessments)
                               ;; Find corresponding administrations
                               (collect-assessment-administrations administrations)
                               ;; Add any missing administrations
                               (add-missing-administrations user-id)
                               ;; Merge assessment and administration info into one map
                               (map #(merge % (get assessments (:assessment-id %)))))]
    (when (seq pending-assessments)
      (add-instruments pending-assessments))))



