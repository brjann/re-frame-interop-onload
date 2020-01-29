(ns bass4.assessment.flagger
  (:require [clj-time.core :as t]
            [clojure.core.async :refer [put!]]
            [bass4.db.core :as db]
            [bass4.assessment.reminder :as assessment-reminder]
            [bass4.services.bass :as bass]
            [bass4.assessment.create-missing :as missing]))

(def oldest-allowed 100)
(def flag-issuer "tLateAdministrationsFlagger")
(def flag-reopen-text "Flag reopened by Flagger")
(def reflag-delay 7)

(defn- db-late-flag-participant-administrations
  [db date]
  (db/get-late-flag-participant-administrations db {:date           date
                                                    :oldest-allowed (t/minus date (t/days oldest-allowed))
                                                    :issuer         flag-issuer}))

(defn- db-late-flag-group-administrations
  [db date]
  (db/get-late-flag-group-administrations db {:date           date
                                              :oldest-allowed (t/minus date (t/days oldest-allowed))
                                              :issuer         flag-issuer}))

(defn- db-reopen-flags!
  [db flag-texts]
  (when (seq flag-texts)
    (db/reopen-flags! db {:flag-texts flag-texts})))

(defn- db-reopen-flag-comments!
  [db flag-parents comment-text]
  (when (seq flag-parents)
    (db/reopen-flag-comments! db {:comment-parents flag-parents
                                  :comment-text    comment-text})))

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

(defn flag-text
  [now assessment]
  (let [activation-date (if (= 0 (:scope assessment))
                          (:participant-activation-date assessment)
                          (:group-activation-date assessment))]
    (str "Completion of assessment "
         (:assessment-name assessment)
         " is late by "
         (t/in-days
           (t/interval activation-date now))
         " days")))

(defn create-flag!
  [db now flag-id assessment]
  (let [text              (flag-text now assessment)
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
                                      "ReflagDelay"    reflag-delay
                                      "Issuer"         flag-issuer
                                      "ReferenceId"    administration-id
                                      "ClosedAt"       0})))

(def ^:dynamic *create-flag-chan* nil)

(defn flag-late-assessments!
  [db now]
  (let [potentials (->> (potential-flag-assessments db now)
                        (map #(assoc % ::assessment-reminder/remind-type ::flag)))
        ongoing    (when (seq potentials)
                     (->> (assessment-reminder/filter-ongoing-assessments db now potentials)
                          (missing/add-missing-administrations! db)))]
    (let [[have-flags need-flags] (split-with :flag-id ongoing)]
      (when (seq need-flags)
        (let [flag-ids (bass/create-bass-objects-without-parent*! db
                                                                  "cFlag"
                                                                  "Flags"
                                                                  (count need-flags))]
          (doseq [[assessment flag-id] (partition 2 (interleave need-flags flag-ids))]
            (when *create-flag-chan*
              (put! *create-flag-chan* [(:user-id assessment) flag-id]))
            (create-flag! db
                          now
                          flag-id
                          assessment))))
      (when (seq have-flags)
        (db-reopen-flags! db (map (juxt :flag-id #(flag-text now %)) have-flags))
        (let [comment-ids (bass/create-bass-objects-without-parent*! db
                                                                     "cComment"
                                                                     "Comments"
                                                                     (count have-flags))]
          (db-reopen-flag-comments! db
                                    (map #(vector %1 (:flag-id %2)) comment-ids have-flags)
                                    flag-reopen-text))
        (when *create-flag-chan*
          (doseq [{:keys [flag-id user-id]} have-flags]
            (put! *create-flag-chan* [user-id flag-id])))))
    ongoing))