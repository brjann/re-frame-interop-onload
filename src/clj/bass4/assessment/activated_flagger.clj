(ns bass4.assessment.activated-flagger
  (:require [clj-time.core :as t]
            [clojure.core.async :refer [put!]]
            [bass4.assessment.reminder :as assessment-reminder]
            [bass4.assessment.db :as assessment-db]
            [bass4.assessment.create-missing :as missing]
            [clj-time.format :as tf]
            [bass4.db.orm-classes :as orm]))

(def flag-issuer "tActivatedAdministrationsFlagger")
(def oldest-allowed 100)

(defn flag-text
  [tz assessment]
  (let [activation-date (if (= 0 (:scope assessment))
                          (:participant-activation-date assessment)
                          (:group-activation-date assessment))]
    (str "Assessment "
         (:assessment-name assessment)
         " activated "
         (-> (tf/formatter "yyyy-MM-dd" tz)
             (tf/unparse activation-date)))))

(defn create-flag!
  [db tz flag-id assessment]
  (let [text              (flag-text tz assessment)
        user-id           (:user-id assessment)
        administration-id (:participant-administration-id assessment)]
    (orm/update-object-properties*! db
                                    "c_flag"
                                    flag-id
                                    {"ParentId"       user-id
                                     "FlagText"       text
                                     "CustomIcon"     "flag-administration-activated.gif"
                                     "Open"           1
                                     "ReflagPossible" 0
                                     "ReflagDelay"    0
                                     "Issuer"         flag-issuer
                                     "ReferenceId"    administration-id
                                     "ClosedAt"       0}))
  (orm/set-objectlist-parent!
    db
    flag-id
    user-id))

(defn- potential-assessments
  "Returns list of potentially flag assessments for users
  {:user-id 653692,
   :group-id 653637,
   :participant-administration-id nil,
   :group-administration-id 653640,
   :assessment-id 653636,
   :assessment-index 1,}"
  [db now tz]
  (let [participant-administrations (assessment-db/activated-flag-participant-administrations
                                      db
                                      now
                                      tz
                                      flag-issuer
                                      oldest-allowed)
        group-administration        (assessment-db/activated-flag-group-administrations
                                      db
                                      now
                                      tz
                                      flag-issuer
                                      oldest-allowed)]
    (concat participant-administrations
            group-administration)))

(defn flag-assessments!
  [db now tz]
  (let [potentials (potential-assessments db now tz)
        ongoing    (when (seq potentials)
                     (->> (assessment-reminder/filter-ongoing-assessments db now potentials true)
                          (missing/add-missing-administrations! db)))]
    (when (seq ongoing)
      (let [flag-ids (orm/create-bass-objects-without-parent*! db
                                                               "cFlag"
                                                               "Flags"
                                                               (count ongoing))]
        (doseq [[assessment flag-id] (partition 2 (interleave ongoing flag-ids))]
          (create-flag! db
                        tz
                        flag-id
                        assessment))))
    ongoing))

(defn activated-flag-task
  [db local-config now]
  (let [tz (-> (:timezone local-config "Europe/Stockholm")
               (t/time-zone-for-id))]
    (let [res (flag-assessments! db now tz)]
      {:cycles (count res)})))