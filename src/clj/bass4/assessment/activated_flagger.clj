(ns bass4.assessment.activated-flagger
  (:require [clj-time.core :as t]
            [clojure.core.async :refer [put!]]
            [bass4.db.core :as db]
            [bass4.assessment.reminder :as assessment-reminder]
            [bass4.services.bass :as bass]
            [bass4.assessment.create-missing :as missing]
            [clj-time.format :as tf]
            [bass4.db.orm-classes :as orm]
            [bass4.clients.time :as client-time]))

(def flag-issuer "tActivatedAdministrationsFlagger")
(def oldest-allowed 100)

(defn date-intervals
  [now tz]
  (let [midnight (client-time/local-midnight now tz)]
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
                                     "ClosedAt"       0})))

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