(ns bass4.repl-api
  (:require [bass4.services.user :as user-service]
            [bass4.db.core :as db]
            [bass4.assessment.ongoing :as assessment-ongoing]
            [clj-time.core :as t]))

(defn hash-password
  [password]
  (user-service/password-hasher password))

(defn group-administrations-statuses
  [group-id]
  (let [db                   db/*db*
        assessment-series-id (-> (db/get-group-assessment-series db {:group-ids [group-id]})
                                 (first)
                                 :assessment-series-id)
        administrations      (->> (db/get-group-administrations db {:group-id group-id :assessment-series-id assessment-series-id})
                                  (map #(assoc % :active? (:group-administration-active? %)))
                                  (group-by :assessment-id))
        assessments          (db/get-user-assessments db {:assessment-series-id assessment-series-id :parent-id group-id})]
    (map (fn [assessment] (let [assessment-id              (:assessment-id assessment)
                                assessment-administrations (get administrations assessment-id)]
                            (assessment-ongoing/get-administration-statuses (t/now)
                                                                            assessment-administrations
                                                                            assessment)))
         assessments)))