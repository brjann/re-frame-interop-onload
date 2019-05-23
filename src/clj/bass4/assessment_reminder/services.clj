(ns bass4.assessment-reminder.services
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [bass4.utils :as utils]
            [bass4.db.core :as db]))

;; 1. Find ongoing group and participant administrations
;; 2.

; Does not return all group administration when there is at least 1 participant administration
;SELECT
;cp.ObjectId AS `user-id`,
;    cp.Group AS `group-id`,
;    cpa.ObjectId AS `participant-administration-id`,
;    cpa.Assessment AS `participant-assessment-id`,
;    cpa.AssessmentIndex AS `participant-administration-index`,
;    cga.ObjectId AS `group-administration-id`,
;    cga.Assessment AS `group-assessment-id`,
;    cga.AssessmentIndex AS `group-administration-index`
;    FROM
;c_participant AS cp
;LEFT JOIN c_participantadministration AS cpa
;ON cp.ObjectId = cpa.Parentid
;LEFT JOIN c_groupadministration as cga
;ON
;cp.Group = cga.ParentId AND
;((cpa.Assessment = cga.Assessment AND
;                 cpa.AssessmentIndex = cga.AssessmentIndex) OR
;  cpa.Objectid IS NULL)
;WHERE cp.Group IN (642622)

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

(defn get-pending-assessments [user-id]
  (let
    ;; NOTE that administrations is a map of lists
    ;; administrations within one assessment battery
    ;;
    ;; Amazingly enough, this all works even with no pending administrations
    [{:keys [administrations assessments]} (get-user-administrations user-id)
     pending-assessments (->> (vals administrations)
                              ;; Sort administrations by their assessment-index
                              (map #(sort-by :assessment-index %))
                              ;; Return assessment (!) statuses
                              (map #(get-assessment-statuses % assessments))
                              ;; Remove lists within list
                              (flatten)
                              ;; Keep the assessments that are AS_PENDING
                              (filter-pending-assessments)
                              ;; Find corresponding administrations
                              (collect-assessment-administrations administrations)
                              ;; Add any missing administrations
                              (add-missing-administrations user-id)
                              ;; Merge assessment and administration info into one map
                              (map #(merge % (get assessments (:assessment-id %)))))]
    (when (seq pending-assessments)
      (add-instruments pending-assessments))))