(ns bass4.assessment.statuses
  (:require [bass4.db.core :as db]
            [bass4.utils :as utils]
            [bass4.assessment.ongoing :as assessment-ongoing]))

(defn- user+group-administrations
  "Returns all administrations for a user with flag for
  whether they belong to correct assessment-series or not"
  [db user-id assessment-series-id]
  (let [group-id                    (assessment-ongoing/user-group db user-id)
        group-administrations       (when group-id
                                      (db/get-group-administrations
                                        db
                                        {:group-id             group-id
                                         :assessment-series-id assessment-series-id}))
        participant-administrations (db/get-all-participant-administrations db {:user-id user-id})
        merged                      (assessment-ongoing/merge-participant-group-administrations
                                      user-id participant-administrations group-administrations)]
    (map #(assoc
            %
            :in-assessment-series?
            (utils/in? [user-id assessment-series-id] (:assessment-series-id %)))
         merged)))


(defn group-administrations-statuses
  [db now group-id]
  (let [assessment-series-id (-> (db/get-group-assessment-series db {:group-ids [group-id]})
                                 (first)
                                 :assessment-series-id)
        administrations      (->> (db/get-group-administrations db {:group-id             group-id
                                                                    :assessment-series-id assessment-series-id})
                                  (group-by :assessment-id))
        assessments          (db/get-user-assessments db {:assessment-series-ids [assessment-series-id]
                                                          :parent-id             group-id})]
    (->> assessments
         (map (fn [assessment] (assessment-ongoing/get-administration-statuses
                                 now
                                 (get administrations (:assessment-id assessment))
                                 assessment)))
         (flatten)
         (filter identity)
         (filter #(not= ::assessment-ongoing/as-scoped-missing (:status %))))))


(defn user-administrations-statuses
  [db now user-id]
  (let [assessment-series-id     (assessment-ongoing/user-assessment-series-id db user-id)
        administrations          (->> (user+group-administrations db user-id assessment-series-id))
        assessments-map          (assessment-ongoing/assessments
                                   db
                                   user-id
                                   (into #{} (map :assessment-series-id administrations)))
        administrations-statuses (->> administrations
                                      (group-by #(:assessment-id %))
                                      (map (fn [[assessment-id administrations]]
                                             (-> administrations
                                                 (#(sort-by :assessment-index %))
                                                 (#(assessment-ongoing/get-administration-statuses now % (get assessments-map assessment-id))))))
                                      (flatten))]
    (filter #(not= ::assessment-ongoing/as-scoped-missing (:status %)) administrations-statuses)))