(ns bass4.assessment.flagger
  (:require [clj-time.core :as t]
            [bass4.db.core :as db]
            [bass4.assessment.reminder :as assessment-reminder]
            [bass4.services.bass :as bass]
            [clojure.tools.logging :as log]
            [bass4.time :as b-time]))

(def oldest-allowed 100)
(def flag-issuer "tLateAdministrationsFlagger")

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
   :assessment-index 1,}"
  [db now]
  (let [participant-administrations (db-late-flag-participant-administrations db now)
        group-administration        (db-late-flag-group-administrations db now)]
    (concat participant-administrations
            group-administration)))

(defn create-flag!
  [db now flag-id assessment]
  (let [activation-date   (if (= 0 (:scope assessment))
                            (:participant-activation-date assessment)
                            (:group-activation-date assessment))
        text              (str "Completion of assessment "
                               (:assessment-name assessment)
                               " is late by "
                               (t/in-days
                                 (t/interval activation-date now))
                               " days")
        user-id           (:user-id assessment)
        administration-id (:participant-administration-id assessment)]
    (bass/update-object-properties*! db
                                     "c_flag"
                                     flag-id
                                     {"ParentId"       user-id
                                      "FlagText"       text
                                      "CustomIcon"     "flag-administration-late.gif"
                                      "Open"           1
                                      "ReflagPossible" 1
                                      "ReflagDelay"    7
                                      "Issuer"         flag-issuer
                                      "ReferenceId"    administration-id
                                      "ClosedAt"       0})))

(defn flag-late-assessments!
  [db now]
  (let [potentials (->> (potential-flag-assessments db now)
                        (map #(assoc % ::assessment-reminder/remind-type ::flag)))
        ongoing    (when (seq potentials)
                     (assessment-reminder/ongoing-reminder-assessments db now potentials))
        flag-ids   (bass/create-bass-objects-without-parent*! db "cFlag" "Flags" (count ongoing))]
    (doseq [[assessment flag-id] (partition 2 (interleave ongoing flag-ids))]
      (create-flag! db
                    now
                    flag-id
                    assessment))
    ongoing))