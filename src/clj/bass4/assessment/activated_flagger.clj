(ns bass4.assessment.activated-flagger
  (:require [clj-time.core :as t]
            [clojure.core.async :refer [put!]]
            [bass4.db.core :as db]
            [bass4.assessment.reminder :as assessment-reminder]
            [bass4.services.bass :as bass]
            [bass4.assessment.create-missing :as missing]
            [clojure.tools.logging :as log]))

(def flag-issuer "tActivatedAdministrationsFlagger")
(def oldest-allowed 100)

(defn date-intervals
  [now tz]
  (let [midnight (bass/local-midnight now tz)]
    [(-> midnight
         (t/minus (t/days oldest-allowed)))
     (-> midnight
         (t/plus (t/days 1))
         (t/minus (t/seconds 1)))]))

(defn- db-participant-administrations
  [db now tz]
  (let [[date-min date-max] (date-intervals now tz)]
    (db/get-flagging-activated-participant-administrations db {:date-max date-max
                                                               :date-min date-min
                                                               :issuer   flag-issuer})))

(defn- db-group-administrations
  [db now tz]
  (let [[date-min date-max] (date-intervals now tz)]
    (db/get-flagging-activated-group-administrations db {:date-max date-max
                                                         :date-min date-min
                                                         :issuer   flag-issuer})))

(defn- potential-assessments
  "Returns list of potentially flag assessments for users
  {:user-id 653692,
   :group-id 653637,
   :participant-administration-id nil,
   :group-administration-id 653640,
   :assessment-id 653636,
   :assessment-index 1,}"
  [db now tz]
  (let [participant-administrations (db-participant-administrations db now tz)
        group-administration        (db-group-administrations db now tz)]
    (concat participant-administrations
            group-administration)))

(defn flag-assessments!
  [db now tz]
  (let [potentials (potential-assessments db now tz)
        ongoing    (when (seq potentials)
                     (->> (assessment-reminder/filter-ongoing-assessments db now potentials)
                          (missing/add-missing-administrations! db)))]

    ongoing))