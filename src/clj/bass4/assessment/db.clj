(ns bass4.assessment.db
  (:require [bass4.db.core :as db]
            [bass4.clients.time :as client-time]
            [clj-time.core :as t]))

;; ------------------
;;  ACTIVATED FLAGS
;; ------------------

(defn- date-intervals
  [now tz oldest-allowed]
  (let [midnight (client-time/local-midnight now tz)]
    [(-> midnight
         (t/minus (t/days oldest-allowed)))
     (-> midnight
         (t/plus (t/days 1))
         (t/minus (t/seconds 1)))]))

(defn ^:dynamic potential-activated-flag-participant-administrations
  "Returns participant administrations that are potentially flaggable as activated.
  If participants' group lacks a matching participant administration,
  :group-administration-id is nil. If they are not in a group, group-id is nil.
  KEYS
  :user-id :group-id
  :participant-administration-id :group-administration-id
  :assessment-id :assessment-index"
  [db now tz flag-issuer oldest-allowed]
  (let [[date-min date-max] (date-intervals now tz oldest-allowed)]
    (db/potential-activated-flag-participant-administrations db {:date-max date-max
                                                                 :date-min date-min
                                                                 :issuer   flag-issuer})))

(defn ^:dynamic potential-activated-flag-group-administrations
  "Returns group administrations that are potentially flaggable as activated.
  Note that it is the participants of the owning groups are returned - rather
  than one row per group. If the participants lacks a matching participant
  administration, :participant-administration-id is nil
  KEYS
  :user-id :group-id
  :participant-administration-id :group-administration-id
  :assessment-id :assessment-index"
  [db now tz flag-issuer oldest-allowed]
  (let [[date-min date-max] (date-intervals now tz oldest-allowed)]
    (db/potential-activated-flag-group-administrations db {:date-max date-max
                                                           :date-min date-min
                                                           :issuer   flag-issuer})))

;; ------------------
;;     LATE FLAGS
;; ------------------

(defn ^:dynamic open-late-administration-flags
  [db flag-issuer]
  (db/get-open-late-administration-flags db {:issuer flag-issuer}))

(defn ^:dynamic potential-late-flag-participant-administrations
  "Returns participant administrations that are potentially flaggable as late.
  If participants' group lacks a matching participant administration,
  :group-administration-id is nil. If they are not in a group, group-id is nil.
  If there is already a flag present, but it is reflaggable, its flag-id is
  also included, else nil.
  KEYS
  :user-id :group-id
  :participant-administration-id :group-administration-id
  :assessment-id :assessment-index
  :flag-id"
  [db date flag-issuer oldest-allowed]
  (db/potential-late-flag-participant-administrations db {:date           date
                                                          :oldest-allowed (t/minus date (t/days oldest-allowed))
                                                          :issuer         flag-issuer}))

(defn ^:dynamic potential-late-flag-group-administrations
  "Returns group administrations that are potentially flaggable as late.
  Note that it is the participants of the owning groups are returned - rather
  than one row per group. If the participants lacks a matching participant
  administration, :participant-administration-id is nil
  If there is already a flag present, but it is reflaggable, its flag-id is
  also included, else nil.
  KEYS
  :user-id :group-id
  :participant-administration-id :group-administration-id
  :assessment-id :assessment-index
  :flag-id"
  [db date flag-issuer oldest-allowed]
  (db/potential-late-flag-group-administrations db {:date           date
                                                    :oldest-allowed (t/minus date (t/days oldest-allowed))
                                                    :issuer         flag-issuer}))

;; ------------------
;;      REMINDER
;; ------------------

(defn users-assessment-series
  "Returns the assessment series id for each user."
  [db user-ids]
  (when (seq user-ids)
    (db/get-user-assessment-series db {:user-ids user-ids})))

(defn groups-assessment-series
  "Returns the assessment series id for each group."
  [db group-ids]
  (when (seq group-ids)
    (db/get-group-assessment-series db {:group-ids group-ids})))

(defn ^:dynamic potential-activated-remind-participant-administrations
  "Returns participant administrations that should potentially receive activation
  reminders. If participants' group lacks a matching participant administration,
  :group-administration-id is nil. If they are not in a group, group-id is nil.
  KEYS
  :user-id :group-id
  :participant-administration-id :group-administration-id
  :assessment-id :assessment-index
  :late-reminders-sent"
  [db date-min date-max hour]
  (db/potential-activated-remind-participant-administrations db {:date-min date-min
                                                                 :date-max date-max
                                                                 :hour     hour}))


(defn ^:dynamic potential-activated-remind-group-administrations
  "Returns group administrations that should potentially receive activation
  reminders. Note that it is the participants of the owning groups are returned
  - rather than one row per group. If the participants lacks a matching participant
  administration, :participant-administration-id is nil
  KEYS
  :user-id :group-id
  :participant-administration-id :group-administration-id
  :assessment-id :assessment-index
  :late-reminders-sent"
  [db date-min date-max hour]
  (db/potential-activated-remind-group-administrations db {:date-min date-min
                                                           :date-max date-max
                                                           :hour     hour}))

(defn ^:dynamic potential-late-remind-participant-administrations
  "Returns participant administrations that should potentially receive late
  reminders. If participants' group lacks a matching participant administration,
  :group-administration-id is nil. If they are not in a group, group-id is nil.
  KEYS
  :user-id :group-id
  :participant-administration-id :group-administration-id
  :assessment-id :assessment-index
  :late-reminders-sent"
  [db date]
  (db/potential-late-remind-participant-administrations db {:date date}))

(defn ^:dynamic potential-late-remind-group-administrations
  "Returns group administrations that should potentially receive late
  reminders. Note that it is the participants of the owning groups are returned
  - rather than one row per group. If the participants lacks a matching participant
  administration, :participant-administration-id is nil
  KEYS
  :user-id :group-id
  :participant-administration-id :group-administration-id
  :assessment-id :assessment-index
  :late-reminders-sent"
  [db date]
  (db/potential-late-remind-group-administrations db {:date date}))

(defn participant-administrations-by-user+assessment+series
  "Receives a vector of vectors
  [[group-id assessment-id assessment-series-id] ...]
  and returns their corresponding group administrations.
  KEYS
  :user-id
  :date-completed :participant-activation-date
  :participant-administration-active?
  :participant-administration-id :group-administration-id
  :assessment-id :assessment-index"
  [db user+assessments+series]
  (when (seq user+assessments+series)
    (db/participant-administrations-by-user+assessment+series
      db
      {:user-ids+assessment-ids user+assessments+series})))
;cpa.ParentId AS `user-id`,
;    cpa.ObjectId AS `participant-administration-id`,
;    ca.ObjectId AS `assessment-id`,
;    cpa.AssessmentIndex AS `assessment-index`,
;    cpa.Active AS `participant-administration-active?`,
;
;    (CASE
;      WHEN cpa.DateCompleted IS NULL
;      THEN 0
;      ELSE cpa.DateCompleted
;      END ) AS `date-completed`,
;
;    (CASE
;      WHEN cpa.`Date` IS NULL OR cpa.`Date` = 0
;      THEN NULL
;      ELSE from_unixtime(cpa.`Date`)
;                             END) AS `participant-activation-date`

(defn group-administrations-by-user+assessment+series
  "Receives a vector of vectors
  [[group-id assessment-id assessment-series-id] ...]
  and returns their corresponding group administrations.
  KEYS
  :group-id
  :group-activation-date :group-administration-active?
  :participant-administration-id :group-administration-id
  :assessment-id :assessment-index"
  [db groups+assessments+series]
  (db/group-administrations-by-user+assessment+series
    db
    {:group-ids+assessment-ids groups+assessments+series}))


(defn assessments-by-assessment-id
  "Returns a map of assessments keyed by assessment-id"
  [db assessment-ids]
  (when assessment-ids
    (->> (db/assessments db {:assessment-ids assessment-ids})
         (map #(vector (:assessment-id %) %))
         (into {}))))

;; ------------------
;;       ONGOING
;; ------------------

(defn user-assessment-series-id
  "Returns the assessment series for a user."
  [db user-id]
  (when user-id
    (-> (users-assessment-series db [user-id])
        (first)
        :assessment-series-id)))

(defn merge-participant-group-administrations
  "Merge participant and group administration for a user that belong to the same assessment"
  [user-id participant-administrations group-administrations]
  (->> (concat participant-administrations group-administrations) ;; From https://stackoverflow.com/a/20808420
       (sort-by (juxt :assessment-id :assessment-index))
       (partition-by (juxt :assessment-id :assessment-index))
       (map (partial apply merge))
       (map #(assoc % :user-id user-id))))

(defn group-administrations-by-assessment-series
  "Returns the group's administrations belonging to an assessment series.
  Clinician rated are not included because they cannot be ongoing for a user."
  [db group-id assessment-series-id]
  (db/group-administrations-by-assessment-series
    db
    {:group-id             group-id
     :assessment-series-id assessment-series-id}))

(defn participant-administrations-by-assessment-series
  "Returns the participants's administrations belonging to an assessment series
  Clinician rated are not included because they cannot be ongoing for a user."
  [db user-id assessment-series-id]
  (db/get-participant-administrations-by-assessment-series
    db
    {:user-id              user-id
     :assessment-series-id assessment-series-id}))

(defn user-assessments
  [db user-id assessment-series-ids]
  (when (seq assessment-series-ids)
    (db/get-user-assessments db {:assessment-series-ids assessment-series-ids :parent-id user-id})))

(defn user-assessments-by-assessment-id
  [db user-id assessment-series-ids]
  (->> (user-assessments db user-id assessment-series-ids)
       (map #(vector (:assessment-id %) %))
       (into {})))

(defn filter-ongoing-assessments
  [assessment-statuses]
  (filter #(and
             (= :assessment-status/ongoing (:status %))
             (not (:is-record? %)))
          assessment-statuses))

;; ------------------
;;      STATUSES
;; ------------------

(defn user-administrations
  [db user-id]
  (db/get-all-participant-administrations db {:user-id user-id}))

(defn group-administrations
  "Returns the group's administrations belonging to an assessment series.
  Both clinician rated and non-clinician rated are included."
  [db group-id assessment-series-id]
  (db/all-group-administrations-by-assessment-series
    db
    {:group-id             group-id
     :assessment-series-id assessment-series-id}))