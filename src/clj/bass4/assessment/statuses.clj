(ns bass4.assessment.statuses
  (:require [bass4.utils :as utils]
            [bass4.assessment.ongoing :as assessment-ongoing]
            [bass4.assessment.db :as assessment-db]
            [bass4.assessment.resolve-status :as status]))

(defn group-administrations-statuses
  [db now group-id]
  (let [assessment-series-id (-> (assessment-db/group-assessment-series db [group-id])
                                 (first)
                                 :assessment-series-id)
        administrations      (->> (assessment-db/group-administrations db
                                                                       group-id
                                                                       assessment-series-id)
                                  (group-by :assessment-id))
        assessments          (assessment-db/user-assessments db
                                                             group-id
                                                             [assessment-series-id])]
    (->> assessments
         (map (fn [assessment] (status/get-administration-statuses
                                 now
                                 (get administrations (:assessment-id assessment))
                                 assessment)))
         (flatten)
         (filter identity)
         #_(filter #(not= :assessment-status/scoped-missing (:status %))))))


(defn- user+group-administrations
  [db user-id assessment-series-id]
  (let [group-id                    (assessment-ongoing/db-user-group db user-id)
        group-administrations       (when group-id
                                      (assessment-db/group-administrations
                                        db
                                        group-id
                                        assessment-series-id))
        participant-administrations (assessment-db/user-administrations db user-id)
        merged                      (assessment-ongoing/merge-participant-group-administrations
                                      user-id participant-administrations group-administrations)]
    merged))

(defn user-administrations-statuses
  [db now user-id]
  (let [assessment-series-id     (assessment-ongoing/user-assessment-series-id db user-id)
        administrations          (user+group-administrations db user-id assessment-series-id)
        assessments-map          (assessment-ongoing/assessments db
                                                                 user-id
                                                                 (into #{} (map :assessment-series-id administrations)))
        administrations-statuses (->> administrations
                                      (group-by #(:assessment-id %))
                                      (map (fn [[assessment-id administrations]]
                                             (-> administrations
                                                 (#(sort-by :assessment-index %))
                                                 (#(status/get-administration-statuses
                                                     now % (get assessments-map assessment-id))))))
                                      (flatten)
                                      (map #(if (or (utils/in? [user-id assessment-series-id] (:assessment-series-id %))
                                                    (= :assessment-status/completed (:status %)))
                                              %
                                              (assoc % :status :assessment-status/wrong-series))))]
    administrations-statuses
    #_(filter #(not= :assessment-status/scoped-missing (:status %)) administrations-statuses)))