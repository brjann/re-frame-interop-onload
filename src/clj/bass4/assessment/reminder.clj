(ns bass4.assessment.reminder
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [bass4.utils :as utils]
            [bass4.db.core :as db]))

(defn activated-participant-administrations
  [db date-min date-max hour]
  (db/get-activated-participant-administrations db {:date-min date-min
                                                    :date-max date-max
                                                    :hour     hour}))

(defn activated-group-administrations
  [db date-min date-max hour]
  (db/get-activated-group-administrations db {:date-min date-min
                                              :date-max date-max
                                              :hour     hour}))

(defn participant-administrations-by-user+assessment
  [db user+assessments]
  (db/get-participant-administrations-by-user+assessment
    db
    {:user-ids+assessment-ids user+assessments}))

(defn group-administrations-by-group+assessment
  [db groups+assessments]
  (db/get-group-administrations-by-group+assessment db {:group-ids+assessment-ids groups+assessments}))

(def tz (t/time-zone-for-id "Asia/Tokyo"))

(defn today-midnight
  [now tz]
  (-> (t/to-time-zone now tz)
      (t/with-time-at-start-of-day)
      (utils/to-unix)))

(defn today-last-second
  [now tz]
  (-> (t/to-time-zone now tz)
      (t/with-time-at-start-of-day)
      (t/plus (t/days 1))
      (utils/to-unix)
      (- 1)))

(defn potential-activation-reminders
  [db now tz hour]
  (let [
        date-min                    (today-midnight now tz)
        date-max                    (today-last-second now tz)
        participant-administrations (activated-participant-administrations db date-min date-max hour)
        group-administrations       (activated-group-administrations db date-min date-max hour)]
    (concat participant-administrations
            group-administrations)))

(defn participant-administrations-from-potential-assessments
  [db potential-assessments]
  (let [user+assessments (into #{} (map #(vector (:user-id %)
                                                 (:assessment-id %))
                                        potential-assessments))
        administrations  (participant-administrations-by-user+assessment db user+assessments)]
    (group-by #(vector (:user-id %) (:assessment-id %)) administrations)))

(defn group-administrations-from-potential-assessments
  [db potential-assessments]
  (let [groups+assessments (into #{} (map #(vector (:group-id %)
                                                   (:assessment-id %))
                                          potential-assessments))
        administrations    (group-administrations-by-group+assessment db groups+assessments)]
    (group-by #(vector (:group-id %) (:assessment-id %)) administrations)))

(defn xx
  []
  (let [potential (potential-activation-reminders db/*db* (t/now) tz 0)]
    (when (seq potential)
      (let [user-groups                         (into {} (map #(vector (:user-id %) (:group-id %)) potential))
            user-assessments                    (into {} (map #(vector (:user-id %) (:assessment-id %)) potential))
            participant-administrations-grouped (participant-administrations-from-potential-assessments db/*db* potential)
            group-administrations-grouped       (group-administrations-from-potential-assessments db/*db* potential)]
        (map (fn [[user-id assessment-id]]
               (let [group-id                    (get user-groups user-id)
                     participant-administrations (get participant-administrations-grouped [user-id assessment-id])
                     group-administrations       (when group-id
                                                   (get group-administrations-grouped [group-id assessment-id]))]
                 ;; From https://stackoverflow.com/a/20808420
                 (->> (concat participant-administrations group-administrations)
                      (sort-by :assessment-index)           ;; split it into groups
                      (partition-by :assessment-index)      ;; by assessment-index
                      (map (partial apply merge)))))        ;; merge each group into a single map.
             user-assessments)))))