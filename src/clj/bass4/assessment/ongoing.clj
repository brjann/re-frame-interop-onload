(ns bass4.assessment.ongoing
  (:require [bass4.db.core :as db]
            [clj-time.core :as t]
            [bass4.utils :refer [key-map-list map-map indices fnil+ diff in? select-values]]
            [bass4.assessment.create-missing :as missing]
            [bass4.services.bass :refer [create-bass-objects-without-parent!]]
            [clojure.tools.logging :as log]))

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

(defn- next-manual-ongoing?
  [{:keys [repetition-type]} next-administration-status]
  (if (nil? next-administration-status)
    false
    (and (= "MANUAL" repetition-type)
         (not= next-administration-status ::as-no-date)
         (not= next-administration-status ::as-inactive))))


(defn- get-administration-status
  [administration next-administration-status assessment]
  (merge (select-keys assessment [:assessment-id :is-record? :assessment-name :clinician-rated?])
         {:assessment-index (:assessment-index administration)
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

                              (next-manual-ongoing? assessment next-administration-status)
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

                                  :else ::as-ongoing)))}))

(defn- get-assessment-statuses
  [administrations assessments]
  (when (seq administrations)
    (let [next-administrations   (get-assessment-statuses (rest administrations) assessments)
          current-administration (first administrations)
          current-assessment     (get assessments (:assessment-id current-administration))]
      (when (nil? current-assessment)
        (throw (Exception. (str "Assessment ID: " (:assessment-id current-administration) " does not exist."))))
      (cons (get-administration-status current-administration (:status (first next-administrations)) current-assessment) next-administrations))))


(defn- filter-ongoing-assessments
  [assessment-statuses]
  (filter #(and
             (= ::as-ongoing (:status %))
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


(defn- user-assessments
  [user-id assessment-series-id]
  (let [assessments (db/get-user-assessments {:assessment-series-id assessment-series-id :user-id user-id})]
    (key-map-list assessments :assessment-id)))

(defn- user-administrations
  [user-id group-id assessment-series-id]
  (let [administrations (db/get-user-administrations {:user-id user-id :group-id group-id :assessment-series-id assessment-series-id})]
    (group-by #(:assessment-id %) administrations)))

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
                               ;; Keep the assessments that are ::as-ongoing
                               (filter-ongoing-assessments)
                               ;; Find corresponding administrations
                               (collect-assessment-administrations administrations)
                               ;; Add any missing administrations
                               (missing/add-missing-administrations! user-id)
                               ;; Merge assessment and administration info into one map
                               (map #(merge % (get assessments (:assessment-id %)))))]
    (when (seq pending-assessments)
      (add-instruments pending-assessments))))



