(ns bass4.assessment.ongoing
  (:require [clj-time.core :as t]
            [bass4.db.core :as db]
            [bass4.utils :as utils]
            [bass4.assessment.create-missing :as missing]
            [bass4.assessment.db :as assessment-db]
            [bass4.assessment.resolve-status :as status]))

(defn db-user-group
  [db user-id]
  (:group-id (db/get-user-group db {:user-id user-id})))

(defn db-assessment-instruments
  [db assessment-ids]
  (db/get-assessments-instruments db {:assessment-ids assessment-ids}))

(defn db-administration-completed-instruments
  [db administration-ids]
  (db/get-administration-completed-instruments db {:administration-ids administration-ids}))

(defn db-administration-additional-instruments
  [db administration-ids]
  (db/get-administration-additional-instruments db {:administration-ids administration-ids}))

(defn user-assessment-series-id
  [db user-id]
  (when user-id
    (:assessment-series-id (first (assessment-db/users-assessment-series db [user-id])))))

(defn merge-participant-group-administrations
  [user-id participant-administrations group-administrations]
  (->> (concat participant-administrations group-administrations) ;; From https://stackoverflow.com/a/20808420
       (sort-by (juxt :assessment-id :assessment-index))
       (partition-by (juxt :assessment-id :assessment-index))
       (map (partial apply merge))
       (map #(assoc % :user-id user-id))))

(defn- user+group-administrations
  [db user-id assessment-series-id]
  (let [group-id                    (db-user-group db user-id)
        group-administrations       (when group-id
                                      (assessment-db/group-administrations db group-id assessment-series-id))
        participant-administrations (assessment-db/participant-administrations-by-assessment-series
                                      db
                                      user-id
                                      assessment-series-id)]
    (merge-participant-group-administrations user-id
                                             participant-administrations
                                             group-administrations)))

(defn filter-ongoing-assessments
  [assessment-statuses include-clinician?]
  (filter #(and
             (= ::as-ongoing (:status %))
             (not (:is-record? %))
             (if include-clinician?
               true
               (not (:clinician-rated? %))))
          assessment-statuses))

(defn- add-instruments
  [db assessments]
  (let [administration-ids     (map :participant-administration-id assessments)
        assessment-instruments (->> assessments
                                    (map :assessment-id)
                                    (db-assessment-instruments db)
                                    (group-by :assessment-id)
                                    (utils/map-map #(map :instrument-id %)))
        completed-instruments  (->> (db-administration-completed-instruments db administration-ids)
                                    (group-by :administration-id)
                                    (utils/map-map #(map :instrument-id %)))
        additional-instruments (->> (db-administration-additional-instruments db administration-ids)
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
  (->> (assessment-db/user-assessments db user-id assessment-series-ids)
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
                                                 (#(status/get-administration-statuses now % (get assessments-map assessment-id))))))
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
