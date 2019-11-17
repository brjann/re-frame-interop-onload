(ns bass4.assessment.ongoing
  (:require [clj-time.core :as t]
            [bass4.db.core :as db]
            [bass4.utils :as utils]
            [bass4.assessment.create-missing :as missing]))

(defn- user-group
  [db user-id]
  (:group-id (db/get-user-group db {:user-id user-id})))

(defn- user-assessment-series-id
  [db user-id]
  (when user-id
    (:assessment-series-id (first (db/get-user-assessment-series db {:user-ids [user-id]})))))

(defn- merge-participant-group-administrations
  [user-id participant-administrations group-administrations]
  (->> (concat participant-administrations group-administrations) ;; From https://stackoverflow.com/a/20808420
       (sort-by (juxt :assessment-id :assessment-index))
       (partition-by (juxt :assessment-id :assessment-index))
       (map (partial apply merge))
       (map #(assoc % :user-id user-id))))

(defn- user-administrations
  [db user-id group-id assessment-series-id]
  (let [group-administrations       (when group-id
                                      (db/get-group-administrations db {:group-id group-id :assessment-series-id assessment-series-id}))
        participant-administrations (db/get-participant-administrations db {:user-id user-id :assessment-series-id assessment-series-id})]
    (merge-participant-group-administrations user-id participant-administrations group-administrations)))

(defn user-assessments
  [db user-id assessment-series-id]
  (db/get-user-assessments db {:assessment-series-id assessment-series-id :parent-id user-id}))

(defn assessment-instruments
  [db assessment-ids]
  (db/get-assessments-instruments db {:assessment-ids assessment-ids}))

(defn administration-completed-instruments
  [db administration-ids]
  (db/get-administration-completed-instruments db {:administration-ids administration-ids}))

(defn administration-additional-instruments
  [db administration-ids]
  (db/get-administration-additional-instruments db {:administration-ids administration-ids}))

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
  [now administration next-administration-status assessment]
  (merge administration
         (select-keys assessment [:assessment-id :is-record? :assessment-name :clinician-rated?])
         {:status (cond
                    (= (:participant-administration-id administration)
                       (:group-administration-id administration) nil)
                    ::as-all-missing

                    (and (= (:scope assessment) 0)
                         (nil? (:participant-administration-id administration)))
                    ::as-own-missing

                    (and (= (:scope assessment) 1)
                         (nil? (:group-administration-id administration)))
                    ::as-group-missing

                    (and (some? (:date-completed administration))
                         (> (:date-completed administration) 0))
                    ::as-completed

                    (> (:assessment-index administration) (:repetitions assessment))
                    ::as-superfluous

                    (not (and (if (contains? administration :group-administration-active?)
                                (:group-administration-active? administration)
                                true)
                              (if (contains? administration :participant-administration-active?)
                                (:participant-administration-active? administration)
                                true)))
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
                        (nil? activation-date)
                        ::as-no-date

                        (t/before? now activation-date)
                        ::as-waiting

                        (and (some? time-limit) (t/after? now (t/plus activation-date (t/days time-limit))))
                        ::as-date-passed

                        :else
                        ::as-ongoing)))}))

(defn- get-administration-statuses
  [now administrations assessment]
  (when (seq administrations)
    (let [next-administrations   (get-administration-statuses now (rest administrations) assessment)
          current-administration (first administrations)]
      (when (nil? assessment)
        (throw (Exception. (str "Assessment ID: " (:assessment-id current-administration) " does not exist."))))
      (cons (get-administration-status
              now
              current-administration
              (:status (first next-administrations))
              assessment)
            next-administrations))))


(defn- filter-ongoing-assessments
  [assessment-statuses]
  (filter #(and
             (= ::as-ongoing (:status %))
             (not (:is-record? %))
             (not (:clinician-rated? %)))
          assessment-statuses))

(defn- add-instruments [db assessments]
  (let [administration-ids     (map :participant-administration-id assessments)
        assessment-instruments (->> assessments
                                    (map :assessment-id)
                                    (assessment-instruments db)
                                    (group-by :assessment-id)
                                    (utils/map-map #(map :instrument-id %)))
        completed-instruments  (->> (administration-completed-instruments db administration-ids)
                                    (group-by :administration-id)
                                    (utils/map-map #(map :instrument-id %)))
        additional-instruments (->> (administration-additional-instruments db administration-ids)
                                    (group-by :administration-id)
                                    (utils/map-map #(map :instrument-id %)))]
    (map #(assoc % :instruments (utils/diff
                                  (concat
                                    (get assessment-instruments (:assessment-id %))
                                    (get additional-instruments (:participant-administration-id %)))
                                  (get completed-instruments (:participant-administration-id %))))
         assessments)))


(defn- assessments
  [db user-id assessment-series-id]
  (->> (user-assessments db user-id assessment-series-id)
       (map #(vector (:assessment-id %) %))
       (into {})))

(defn- administrations-by-assessment
  [db user-id group-id assessment-series-id]
  (->> (user-administrations db user-id group-id assessment-series-id)
       (group-by #(:assessment-id %))))
;
; Plan:
; Break out function that accepts one user's administrations by assessment
; and a map of assessments and returns the ongoing assessments
; This function can then be used on multiple users in the reminder task
;
; Write two SQL functions for groups / participants and merge them
; to produce identical values as administrations-by-assessment
;

(defn ongoing-administrations
  [now administrations assessment]
  (-> administrations
      (#(sort-by :assessment-index %))
      (#(get-administration-statuses now % assessment))
      (filter-ongoing-assessments)))

(defn ongoing-assessments*
  [db now user-id]
  (binding [db/*db* nil]
    (let
      ;; NOTE that administrations is a map of lists
      ;; administrations within one assessment battery
      ;;
      ;; Amazingly enough, this all works even with no ongoing administrations
      ;;
      [group-id                 (user-group db user-id)
       assessment-series-id     (user-assessment-series-id db user-id)
       administrations-map      (administrations-by-assessment db user-id group-id assessment-series-id)
       assessments-map          (assessments db user-id assessment-series-id)
       ongoing-administrations' (flatten (map (fn [[assessment-id administrations]]
                                                (if-let [assessment (get assessments-map assessment-id)]
                                                  (ongoing-administrations now administrations assessment)
                                                  (throw
                                                    (Exception. (str "Assessment ID: " assessment-id " does not exist.")))))
                                              administrations-map))]
      (when (seq ongoing-administrations')
        (->> ongoing-administrations'
             ;; Add any missing administrations
             (map #(assoc % :user-id user-id))
             (missing/add-missing-administrations! db)
             ;; Merge assessment and administration info into one map
             (map #(merge % (get assessments-map (:assessment-id %))))
             (add-instruments db))))))

(defn ongoing-assessments
  [user-id]
  (ongoing-assessments* db/*db* (t/now) user-id))

(defn group-administrations-statuses
  [db now group-id]
  (let [assessment-series-id (-> (db/get-group-assessment-series db {:group-ids [group-id]})
                                 (first)
                                 :assessment-series-id)
        administrations      (->> (db/get-group-administrations db {:group-id group-id :assessment-series-id assessment-series-id})
                                  ;; get-group-administrations uses LEFT JOIN,
                                  ;; returning assessments without group administrations
                                  (filter :group-administration-id)
                                  (group-by :assessment-id))
        assessments          (db/get-user-assessments db {:assessment-series-id assessment-series-id :parent-id group-id})]
    (->> assessments
         (map (fn [assessment] (get-administration-statuses
                                 now
                                 (get administrations (:assessment-id assessment))
                                 assessment)))
         (flatten)
         (filter identity))))

(defn participant-administrations-statuses
  [db now user-id]
  (let [assessment-series-id (user-assessment-series-id db user-id)
        administrations      (->> (db/get-participant-administrations db {:user-id user-id :assessment-series-id assessment-series-id})
                                  (group-by :assessment-id))
        assessments          (db/get-user-assessments db {:assessment-series-id assessment-series-id :parent-id user-id})]
    (->> assessments
         (map (fn [assessment] (get-administration-statuses
                                 now
                                 (get administrations (:assessment-id assessment))
                                 assessment)))
         (flatten)
         (filter identity))))