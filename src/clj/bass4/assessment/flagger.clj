(ns bass4.assessment.flagger
  (:require [clj-time.core :as t]
            [bass4.db.core :as db]
            [bass4.assessment.reminder :as assessment-reminder]))

(def oldest-allowed 100)

(defn- db-late-flag-participant-administrations
  [db date]
  (db/get-late-flag-participant-administrations db {:date           date
                                                    :oldest-allowed (t/minus date (t/days oldest-allowed))}))

(defn- db-late-flag-group-administrations
  [db date]
  (db/get-late-flag-group-administrations db {:date           date
                                              :oldest-allowed (t/minus date (t/days oldest-allowed))}))

(defn- potential-flag-assessments
  "Returns list of potentially flag assessments for users
  {:user-id 653692,
   :group-id 653637,
   :participant-administration-id nil,
   :group-administration-id 653640,
   :assessment-id 653636,
   :assessment-index 1,
   ::remind-type :late}"
  [db now]
  (let [participant-administrations (db-late-flag-participant-administrations db now)
        group-administration        (db-late-flag-group-administrations db now)]
    (concat participant-administrations
            group-administration)))

(defn flag-late-assessments!
  [db now]
  (let [potentials (->> (potential-flag-assessments db now)
                        (map #(assoc % ::assessment-reminder/remind-type ::flag)))
        ongoing    (when (seq potentials)
                     (assessment-reminder/ongoing-reminder-assessments db now potentials))]
    ongoing))