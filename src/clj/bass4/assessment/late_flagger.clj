(ns bass4.assessment.late-flagger
  (:require [clj-time.core :as t]
            [clojure.core.async :refer [put!]]
            [bass4.db.core :as db]
            [bass4.assessment.reminder :as assessment-reminder]
            [bass4.assessment.db :as assessment-db]
            [bass4.assessment.create-missing :as missing]
            [bass4.db.orm-classes :as orm]))

(def oldest-allowed 100)
(def flag-issuer "tLateAdministrationsFlagger")

(def flag-reopen-text "Automatically reopened by Flagger")
(def reflag-delay 7)

(defn- db-open-flags
  [db]
  (db/get-open-late-administration-flags db {:issuer flag-issuer}))

(defn- db-reopen-flags!
  [db flag-texts]
  (when (seq flag-texts)
    (db/reopen-flags! db {:flag-texts flag-texts})))

(defn- db-comment-reopened-flags!
  [db flag-parents comment-text]
  (when (seq flag-parents)
    (db/reopen-flag-comments! db {:comment-parents flag-parents
                                  :comment-text    comment-text})))

(defn db-close-flags!
  [db now administration-ids reflag-possible?]
  (when (seq administration-ids)
    (db/close-administration-late-flags! db {:administration-ids administration-ids
                                             :issuer             flag-issuer
                                             :now                now
                                             :reflag-possible?   reflag-possible?})))

(defn- potential-assessments
  "Returns list of potentially flag assessments for users
  {:user-id 653692,
   :group-id 653637,
   :participant-administration-id nil,
   :group-administration-id 653640,
   :assessment-id 653636,
   :assessment-index 1,}"
  [db now]
  (let [participant-administrations (assessment-db/db-participant-administrations db now flag-issuer oldest-allowed)
        group-administration        (assessment-db/db-group-administrations db now flag-issuer oldest-allowed)]
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
    (orm/update-object-properties*! db
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

(defn reopen-flags!
  [db now reopen-flags]
  (db-reopen-flags! db (map (juxt :flag-id #(flag-text now %)) reopen-flags))
  (let [comment-ids (orm/create-bass-objects-without-parent*! db
                                                              "cComment"
                                                              "Comments"
                                                              (count reopen-flags))]
    (db-comment-reopened-flags! db
                                (map #(vector %1 (:flag-id %2)) comment-ids reopen-flags)
                                flag-reopen-text))
  (when *create-flag-chan*
    (doseq [{:keys [flag-id user-id]} reopen-flags]
      (put! *create-flag-chan* [user-id flag-id]))))

(defn- new-flags!
  [db now new-flags]
  (let [flag-ids (orm/create-bass-objects-without-parent*! db
                                                           "cFlag"
                                                           "Flags"
                                                           (count new-flags))]
    (doseq [[assessment flag-id] (partition 2 (interleave new-flags flag-ids))]
      (create-flag! db
                    now
                    flag-id
                    assessment)
      (when *create-flag-chan*
        (put! *create-flag-chan* [(:user-id assessment) flag-id])))))

(defn flag-assessments!
  [db now]
  (let [potentials (potential-assessments db now)
        ongoing    (when (seq potentials)
                     (->> (assessment-reminder/filter-ongoing-assessments db now potentials true)
                          (missing/add-missing-administrations! db)))]
    (let [[have-flags need-flags] (split-with :flag-id ongoing)]
      (when (seq have-flags)
        (reopen-flags! db now have-flags))
      (when (seq need-flags)
        (new-flags! db now need-flags)))
    ongoing))

(defn deflag-assessments!
  [db now]
  (let [potentials         (->> (db-open-flags db)
                                ;; An administration can have multiple flags. Keep only one.
                                (map (juxt :participant-administration-id identity))
                                (into {})
                                (vals))
        ongoing-by-flag-id (when (seq potentials)
                             (->> (assessment-reminder/filter-ongoing-assessments db now potentials false)
                                  (group-by :flag-id)))
        inactive           (remove #(contains? ongoing-by-flag-id (:flag-id %)) potentials)]
    (db-close-flags! db
                     now
                     (map :participant-administration-id inactive)
                     true)
    inactive))

(defn late-flag-task
  [db _ now]
  (let [res (flag-assessments! db now)]
    {:cycles (count res)}))

(defn late-deflag-task
  [db _ now]
  (let [res (deflag-assessments! db now)]
    {:cycles (count res)}))