(ns bass4.assessment.statuses
  (:require [clj-time.core :as t]
            [bass4.db.core :as db]
            [bass4.utils :as utils]
            [bass4.assessment.ongoing :as assessment-ongoing]))


(defn group-administrations-statuses
  [db now group-id]
  (let [assessment-series-id (-> (db/get-group-assessment-series db {:group-ids [group-id]})
                                 (first)
                                 :assessment-series-id)
        administrations      (->> (db/get-group-administrations db {:group-id group-id :assessment-series-id assessment-series-id})
                                  (group-by :assessment-id))
        assessments          (db/get-user-assessments db {:assessment-series-id assessment-series-id :parent-id group-id})]
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
        administrations-map      (assessment-ongoing/administrations-by-assessment db user-id assessment-series-id)
        assessments-map          (assessment-ongoing/assessments db user-id assessment-series-id)
        administrations-statuses (->> administrations-map
                                      (map (fn [[assessment-id administrations]]
                                             (-> administrations
                                                 (#(sort-by :assessment-index %))
                                                 (#(assessment-ongoing/get-administration-statuses now % (get assessments-map assessment-id))))))
                                      (flatten))]
    (filter #(not= ::assessment-ongoing/as-scoped-missing (:status %)) administrations-statuses)))