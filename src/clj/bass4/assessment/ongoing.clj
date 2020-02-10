(ns bass4.assessment.ongoing
  (:require [clj-time.core :as t]
            [bass4.db.core :as db]
            [bass4.utils :as utils]
            [bass4.assessment.create-missing :as missing]))

(defn user-group
  [db user-id]
  (:group-id (db/get-user-group db {:user-id user-id})))

(defn user-assessment-series-id
  [db user-id]
  (when user-id
    (:assessment-series-id (first (db/get-user-assessment-series db {:user-ids [user-id]})))))

(defn merge-participant-group-administrations
  [user-id participant-administrations group-administrations]
  (->> (concat participant-administrations group-administrations) ;; From https://stackoverflow.com/a/20808420
       (sort-by (juxt :assessment-id :assessment-index))
       (partition-by (juxt :assessment-id :assessment-index))
       (map (partial apply merge))
       (map #(assoc % :user-id user-id))))

(defn- user+group-administrations
  [db user-id assessment-series-id]
  (let [group-id                    (user-group db user-id)
        group-administrations       (when group-id
                                      (db/get-group-administrations
                                        db
                                        {:group-id             group-id
                                         :assessment-series-id assessment-series-id}))
        participant-administrations (db/get-participant-administrations-by-assessment-series
                                      db
                                      {:user-id              user-id
                                       :assessment-series-id assessment-series-id})]
    (merge-participant-group-administrations user-id participant-administrations group-administrations)))

(defn user-assessments
  [db user-id assessment-series-ids]
  (when (seq assessment-series-ids)
    (db/get-user-assessments db {:assessment-series-ids assessment-series-ids :parent-id user-id})))

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
         (or (= next-administration-status ::as-waiting)
             (= next-administration-status ::as-ongoing)
             (= next-administration-status ::as-date-passed)
             (= next-administration-status ::as-completed)))))

(defn- get-administration-status
  "Does not know about assessment series"
  [now administration next-administration-status assessment]
  (merge administration
         (select-keys assessment [:assessment-id :is-record? :assessment-name :clinician-rated? :scope])
         {:status (cond
                    (= (:participant-administration-id administration)
                       (:group-administration-id administration)
                       nil)
                    (throw (ex-info "No valid administration" administration))

                    (:deleted? administration)
                    ::as-deleted

                    (and (some? (:date-completed administration))
                         (> (:date-completed administration) 0))
                    ::as-completed

                    (false? (:participant-administration-active? administration))
                    ::as-user-inactive

                    (false? (:group-administration-active? administration))
                    ::as-group-inactive

                    ;(not (and (if (contains? administration :group-administration-active?)
                    ;            (:group-administration-active? administration)
                    ;            true)
                    ;          (if (contains? administration :participant-administration-active?)
                    ;            (:participant-administration-active? administration)
                    ;            true)))
                    ;::as-inactive

                    (and (= (:scope assessment) 0)
                         (nil? (:participant-administration-id administration)))
                    ::as-scoped-missing

                    (and (= (:scope assessment) 1)
                         (nil? (:group-administration-id administration)))
                    ::as-scoped-missing

                    (> (:assessment-index administration) (:repetitions assessment))
                    ::as-superfluous

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

(defn get-administration-statuses
  "Does not know about assessment series"
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


(defn filter-ongoing-assessments
  [assessment-statuses include-clinician?]
  (filter #(and
             (= ::as-ongoing (:status %))
             (not (:is-record? %))
             (if include-clinician?
               true
               (not (:clinician-rated? %))))
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


(defn assessments
  [db user-id assessment-series-ids]
  (->> (user-assessments db user-id assessment-series-ids)
       (map #(vector (:assessment-id %) %))
       (into {})))

(defn administrations-by-assessment
  [db user-id assessment-series-id]
  (->> (user+group-administrations db user-id assessment-series-id)
       (group-by #(:assessment-id %))))

(defn user-administration-statuses+assessments
  [db now user-id]
  (let [assessment-series-id     (user-assessment-series-id db user-id)
        administrations-map      (administrations-by-assessment db user-id assessment-series-id)
        assessments-map          (assessments db user-id [assessment-series-id])
        administrations-statuses (->> administrations-map
                                      (map (fn [[assessment-id administrations]]
                                             (-> administrations
                                                 (#(sort-by :assessment-index %))
                                                 (#(get-administration-statuses now % (get assessments-map assessment-id))))))
                                      (flatten))]
    [administrations-statuses assessments-map]))

(defn ongoing-assessments*
  [db now user-id]
  (binding [db/*db* nil]
    (let [[administrations-statuses assessments-map] (user-administration-statuses+assessments db now user-id)
          ongoing (filter-ongoing-assessments administrations-statuses false)]
      (when (seq ongoing)
        (->> ongoing
             ;; Add any missing administrations
             (map #(assoc % :user-id user-id))
             (missing/add-missing-administrations! db)
             ;; Merge assessment and administration info into one map
             (map #(merge % (get assessments-map (:assessment-id %))))
             (add-instruments db))))))


(defn ongoing-assessments
  [user-id]
  (ongoing-assessments* db/*db* (t/now) user-id))
