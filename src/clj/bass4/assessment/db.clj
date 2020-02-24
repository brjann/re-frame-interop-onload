(ns bass4.assessment.db
  (:require [bass4.db.core :as db]
            [bass4.clients.time :as client-time]
            [clj-time.core :as t]
            [clojure.tools.logging :as log]))

;; ------------------
;;  ACTIVATED FLAGS
;; ------------------

(defn date-intervals
  [now tz oldest-allowed]
  (let [midnight (client-time/local-midnight now tz)]
    [(-> midnight
         (t/minus (t/days oldest-allowed)))
     (-> midnight
         (t/plus (t/days 1))
         (t/minus (t/seconds 1)))]))

(defn activated-flag-group-administrations
  [db now tz flag-issuer oldest-allowed]
  (let [[date-min date-max] (date-intervals now tz oldest-allowed)]
    (db/get-flagging-activated-group-administrations db {:date-max date-max
                                                         :date-min date-min
                                                         :issuer   flag-issuer})))

(defn activated-flag-participant-administrations
  [db now tz flag-issuer oldest-allowed]
  (log/debug flag-issuer)
  (log/debug oldest-allowed)
  (let [[date-min date-max] (date-intervals now tz oldest-allowed)]
    (db/get-flagging-activated-participant-administrations db {:date-max date-max
                                                               :date-min date-min
                                                               :issuer   flag-issuer})))

;; ------------------
;;     LATE FLAGS
;; ------------------

(defn db-participant-administrations
  [db date flag-issuer oldest-allowed]
  (db/get-late-flag-participant-administrations db {:date           date
                                                    :oldest-allowed (t/minus date (t/days oldest-allowed))
                                                    :issuer         flag-issuer}))

(defn db-group-administrations
  [db date flag-issuer oldest-allowed]
  (db/get-late-flag-group-administrations db {:date           date
                                              :oldest-allowed (t/minus date (t/days oldest-allowed))
                                              :issuer         flag-issuer}))

;; ------------------
;;       ONGOING
;; ------------------

(defn users-assessment-series
  [db user-ids]
  (db/get-user-assessment-series db {:user-ids user-ids}))

(defn group-administrations
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

;; ------------------
;;      REMINDER
;; ------------------

(defn group-assessment-series
  [db group-ids]
  (when (seq group-ids)
    (db/get-group-assessment-series db {:group-ids group-ids})))

(defn db-activated-participant-administrations
  [db date-min date-max hour]
  (db/get-activated-participant-administrations db {:date-min date-min
                                                    :date-max date-max
                                                    :hour     hour}))


(defn db-activated-group-administrations
  "Returns participants in groups that have potential activated administrations"
  [db date-min date-max hour]
  (db/get-activated-group-administrations db {:date-min date-min
                                              :date-max date-max
                                              :hour     hour}))

(defn db-late-participant-administrations
  [db date]
  (db/get-late-participant-administrations db {:date date}))

(defn db-late-group-administrations
  "Returns participants in groups that have potential late administrations"
  [db date]
  (db/get-late-group-administrations db {:date date}))

(defn db-users-assessment-series-id
  [db user-ids]
  (when (seq user-ids)
    (let [res (users-assessment-series db user-ids)]
      (into {} (map #(vector (:user-id %) (:assessment-series-id %))) res))))

(defn db-groups-assessment-series-id
  [db group-ids]
  (let [res (group-assessment-series db group-ids)]
    (into {} (map #(vector (:group-id %) (:assessment-series-id %))) res)))

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
;;      STATUSES
;; ------------------

(defn user-administrations
  [db user-id]
  (db/get-all-participant-administrations db {:user-id user-id}))