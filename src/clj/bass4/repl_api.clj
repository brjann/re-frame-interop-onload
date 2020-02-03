(ns bass4.repl-api
  (:require [bass4.services.user :as user-service]
            [bass4.db.core :as db]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.assessment.statuses :as assessment-statuses]
            [clj-time.core :as t]))

(defapi hash-password
  [password :- [[api/str? 1 100]]]
  (user-service/password-hasher password))

(defapi user-assessment-statuses
  [db-name :- [[api/str? 1 30]] user-id :- api/->int]
  (let [db (when-let [db- (get db/db-connections (keyword db-name))]
             @db-)]
    (if db
      (->> (assessment-statuses/user-administrations-statuses db/*db*
                                                              (t/now)
                                                              user-id)
           (map (fn [assessment]
                  {"assessment-id"     (:assessment-id assessment)
                   "assessment-index"  (:assessment-index assessment)
                   "administration-id" (:participant-administration-id assessment)
                   "status"            (name (:status assessment))})))
      "No such DB")))

(defapi group-assessment-statuses
  [db-name :- [[api/str? 1 30]] group-id :- api/->int]
  (let [db (when-let [db- (get db/db-connections (keyword db-name))]
             @db-)]
    (if db
      (->> (assessment-statuses/group-administrations-statuses db/*db*
                                                               (t/now)
                                                               group-id)
           (map (fn [assessment]
                  {"assessment-id"     (:assessment-id assessment)
                   "assessment-index"  (:assessment-index assessment)
                   "administration-id" (:group-administration-id assessment)
                   "status"            (name (:status assessment))})))
      "No such DB")))