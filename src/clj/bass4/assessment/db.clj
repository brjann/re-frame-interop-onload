(ns bass4.assessment.db
  (:require [bass4.db.core :as db]
            [bass4.clients.time :as client-time]
            [clj-time.core :as t]
            [clojure.tools.logging :as log]))

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

(defn potential-activated-flag-participant-administrations
  "Returns participant administrations that are potentially flaggable as activated.
  If participants' group lacks a matching participant administration,
  :group-administration-id is nil. If they are not in a group, group-id is nil."
  [db now tz flag-issuer oldest-allowed]
  (let [[date-min date-max] (date-intervals now tz oldest-allowed)]
    (db/potential-activated-flag-participant-administrations db {:date-max date-max
                                                                 :date-min date-min
                                                                 :issuer   flag-issuer})))

(defn potential-activated-flag-group-administrations
  "Returns group administrations that are potentially flaggable as activated.
  Note that it is the participants of the owning groups are returned - rather
  than one row per group. If the participants lacks a matching participant
  administration, :participant-administration-id is nil"
  [db now tz flag-issuer oldest-allowed]
  (let [[date-min date-max] (date-intervals now tz oldest-allowed)]
    (db/potential-activated-flag-group-administrations db {:date-max date-max
                                                           :date-min date-min
                                                           :issuer   flag-issuer})))

;; ------------------
;;     LATE FLAGS
;; ------------------

(defn potential-late-flag-participant-administrations
  "Returns participant administrations that are potentially flaggable as late.
  If participants' group lacks a matching participant administration,
  :group-administration-id is nil. If they are not in a group, group-id is nil."
  [db date flag-issuer oldest-allowed]
  (db/potential-late-flag-participant-administrations db {:date           date
                                                          :oldest-allowed (t/minus date (t/days oldest-allowed))
                                                          :issuer         flag-issuer}))

(defn potential-late-flag-group-administrations
  "Returns group administrations that are potentially flaggable as late.
  Note that it is the participants of the owning groups are returned - rather
  than one row per group. If the participants lacks a matching participant
  administration, :participant-administration-id is nil"
  [db date flag-issuer oldest-allowed]
  (db/potential-late-flag-group-administrations db {:date           date
                                                    :oldest-allowed (t/minus date (t/days oldest-allowed))
                                                    :issuer         flag-issuer}))

;; ------------------
;;      REMINDER
;; ------------------

(defn users-assessment-series
  "Returns the assessment series for each user."
  [db user-ids]
  (when (seq user-ids)
    (db/get-user-assessment-series db {:user-ids user-ids})))

(defn groups-assessment-series
  [db group-ids]
  (when (seq group-ids)
    (db/get-group-assessment-series db {:group-ids group-ids})))

(defn potential-activated-remind-participant-administrations
  "Returns participant administrations that should potentially receive activation
  reminders. If participants' group lacks a matching participant administration,
  :group-administration-id is nil. If they are not in a group, group-id is nil."
  [db date-min date-max hour]
  (db/potential-activated-remind-participant-administrations db {:date-min date-min
                                                                 :date-max date-max
                                                                 :hour     hour}))


(defn potential-activated-remind-group-administrations
  "Returns group administrations that should potentially receive activation
  reminders. Note that it is the participants of the owning groups are returned
  - rather than one row per group. If the participants lacks a matching participant
  administration, :participant-administration-id is nil"
  [db date-min date-max hour]
  (db/potential-activated-remind-group-administrations db {:date-min date-min
                                                           :date-max date-max
                                                           :hour     hour}))

(defn potential-late-remind-participant-administrations
  "Returns participant administrations that should potentially receive late
  reminders. If participants' group lacks a matching participant administration,
  :group-administration-id is nil. If they are not in a group, group-id is nil."
  [db date]
  (db/potential-late-remind-participant-administrations db {:date date}))

(defn potential-late-remind-group-administrations
  "Returns group administrations that should potentially receive late
  reminders. Note that it is the participants of the owning groups are returned
  - rather than one row per group. If the participants lacks a matching participant
  administration, :participant-administration-id is nil"
  [db date]
  (db/potential-late-remind-group-administrations db {:date date}))

(defn db-participant-administrations-by-user+assessment+series
  [db user+assessments+series]
  (db/get-participant-administrations-by-user+assessment
    db
    {:user-ids+assessment-ids user+assessments+series}))

(defn db-group-administrations-by-group+assessment+series
  [db groups+assessments+series]
  (db/get-group-administrations-by-group+assessment db {:group-ids+assessment-ids groups+assessments+series}))


(defn db-assessments
  [db assessment-ids]
  (when assessment-ids
    (->> (db/get-remind-assessments db {:assessment-ids assessment-ids})
         (map #(vector (:assessment-id %) %))
         (into {}))))

;; ------------------
;;       ONGOING
;; ------------------

(defn user-assessment-series-id
  "Returns the assessment series for a user."
  [db user-id]
  (when user-id
    (:assessment-series-id (first (users-assessment-series db [user-id])))))

(defn merge-participant-group-administrations
  "Merge participant and group administration for a user that belong to the same assessment"
  [user-id participant-administrations group-administrations]
  (->> (concat participant-administrations group-administrations) ;; From https://stackoverflow.com/a/20808420
       (sort-by (juxt :assessment-id :assessment-index))
       (partition-by (juxt :assessment-id :assessment-index))
       (map (partial apply merge))
       (map #(assoc % :user-id user-id))))

(defn group-administrations
  "Returns the group's administrations belonging to an assessment series"
  [db group-id assessment-series-id]
  (db/get-group-administrations
    db
    {:group-id             group-id
     :assessment-series-id assessment-series-id}))

(defn participant-administrations-by-assessment-series
  [db user-id assessment-series-id]
  (db/get-participant-administrations-by-assessment-series
    db
    {:user-id              user-id
     :assessment-series-id assessment-series-id}))

(defn user-assessments
  [db user-id assessment-series-ids]
  (when (seq assessment-series-ids)
    (db/get-user-assessments db {:assessment-series-ids assessment-series-ids :parent-id user-id})))

(defn assessments
  [db user-id assessment-series-ids]
  (->> (user-assessments db user-id assessment-series-ids)
       (map #(vector (:assessment-id %) %))
       (into {})))

(defn filter-ongoing-assessments
  [assessment-statuses include-clinician?]
  (filter #(and
             (= :assessment-status/ongoing (:status %))
             (not (:is-record? %))
             (if include-clinician?
               true
               (not (:clinician-rated? %))))
          assessment-statuses))

;; ------------------
;;      STATUSES
;; ------------------

(defn user-administrations
  [db user-id]
  (db/get-all-participant-administrations db {:user-id user-id}))