(ns bass4.assessment.ongoing-many
  (:require [bass4.assessment.db :as assessment-db]
            [bass4.assessment.resolve-status :as status]))


(defn- assessment-series-id-by-user-id
  "Returns map with user-id as keys and assessment-series-id as values"
  [db user-ids]
  (let [res (assessment-db/users-assessment-series db user-ids)]
    (into {} (map #(vector (:user-id %) (:assessment-series-id %))) res)))

(defn- assessment-series-id-by-group-id
  "Returns map with group-id as keys and assessment-series-id as values"
  [db group-ids]
  (let [res (assessment-db/groups-assessment-series db group-ids)]
    (into {} (map #(vector (:group-id %) (:assessment-series-id %))) res)))

(defn- participant-administrations-from-potential-assessments
  "Returns all administrations for user-assessment combo,
  grouped by [user-id assessment-id"
  [db potential-assessments]
  (let [assessment-series       (->> potential-assessments
                                     (map :user-id)
                                     (assessment-series-id-by-user-id db))
        user+assessments+series (->> potential-assessments
                                     (map #(vector (:user-id %)
                                                   (:assessment-id %)
                                                   (get assessment-series (:user-id %))))
                                     (into #{}))
        administrations         (assessment-db/remind-participant-administrations-by-user+assessment+series
                                  db user+assessments+series)]
    (group-by #(vector (:user-id %) (:assessment-id %)) administrations)))

(defn- group-administrations-from-potential-assessments
  "Returns all administrations for group-assessment combo,
  grouped by [group-id assessment-id"
  [db potential-assessments]
  (let [potential-assessments     (filter :group-id potential-assessments)
        assessment-series         (->> potential-assessments
                                       (map :group-id)
                                       (assessment-series-id-by-group-id db))
        groups+assessments+series (->> potential-assessments
                                       (map #(vector (:group-id %)
                                                     (:assessment-id %)
                                                     (get assessment-series (:group-id %))))
                                       (into #{}))
        administrations           (assessment-db/remind-group-administrations-by-user+assessment+series
                                    db groups+assessments+series)]
    (group-by #(vector (:group-id %) (:assessment-id %)) administrations)))

(defn- merge-participant-group-administrations
  [user-id participant-administrations group-administrations]
  (->> (concat participant-administrations group-administrations) ;; From https://stackoverflow.com/a/20808420
       (sort-by :assessment-index)
       (partition-by :assessment-index)
       (map (partial apply merge))
       (map #(assoc % :user-id user-id))))

(defn- ongoing-administrations
  [now administrations assessment include-clinician?]
  (-> administrations
      (#(sort-by :assessment-index %))
      (#(status/get-administration-statuses now % assessment))
      (assessment-db/filter-ongoing-assessments include-clinician?)))

(defn- ongoing-from-potentials
  "Returns list of ALL ongoing assessments based on list of potentials.
  Note, ALL means that ongoing assessment that are not part of potentials may be returned"
  [db now potentials include-clinician?]
  (let [user-groups                         (->> potentials
                                                 (map #(vector (:user-id %) (:group-id %)))
                                                 (filter #(second %))
                                                 (into {}))
        participant-administrations-grouped (participant-administrations-from-potential-assessments db potentials)
        group-administrations-grouped       (when-not (empty? user-groups)
                                              (group-administrations-from-potential-assessments db potentials))
        assessments'                        (assessment-db/db-assessments db (->> potentials
                                                                                  (map :assessment-id)
                                                                                  (into #{})))
        merged-by-user+assessment           (->> potentials
                                                 (map #(vector (:user-id %) (:assessment-id %)))
                                                 (map (fn [[user-id assessment-id]]
                                                        (let [group-id                    (get user-groups user-id)
                                                              participant-administrations (get participant-administrations-grouped [user-id assessment-id])
                                                              group-administrations       (when group-id
                                                                                            (get group-administrations-grouped [group-id assessment-id]))
                                                              merged-administrations      (merge-participant-group-administrations
                                                                                            user-id
                                                                                            participant-administrations
                                                                                            group-administrations)]
                                                          [[user-id assessment-id] merged-administrations])))
                                                 (into {}))
        ongoing-assessments                 (->> merged-by-user+assessment
                                                 (mapv (fn [[[_ assessment-id] administrations]]
                                                         (ongoing-administrations now
                                                                                  administrations
                                                                                  (get assessments' assessment-id)
                                                                                  include-clinician?)))
                                                 (flatten)
                                                 (map #(merge % (get assessments' (:assessment-id %)))))]
    ongoing-assessments))

(defn filter-ongoing-assessments
  "Receives a sequence of potentially ongoing assessments
  and returns the ones that are actually ongoing."
  ([db now potentials] (filter-ongoing-assessments db now potentials false))
  ([db now potentials include-clinician?]
   (when (seq potentials)
     (let [ongoing-assessments                (ongoing-from-potentials db now potentials include-clinician?)
           potential-by+user+assessment+index (->> potentials
                                                   (map #(vector [(:user-id %) (:assessment-id %) (:assessment-index %)] %))
                                                   (into {}))
           filtered-ongoing-potentials        (->> ongoing-assessments
                                                   (map (fn [ongoing]
                                                          (let [potential (get potential-by+user+assessment+index
                                                                               [(:user-id ongoing)
                                                                                (:assessment-id ongoing)
                                                                                (:assessment-index ongoing)])]
                                                            (when potential
                                                              (merge ongoing potential)))))
                                                   (filter identity))]
       filtered-ongoing-potentials))))
