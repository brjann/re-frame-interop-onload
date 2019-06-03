(ns bass4.assessment.reminder
  (:require [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [bass4.utils :as utils]
            [bass4.db.core :as db]
            [clojure.tools.logging :as log]
            [clojure.pprint :as pprint]
            [bass4.assessment.ongoing :as assessment-ongoing]))

(defn- users-assessment-series-id
  [db user-ids]
  (when user-ids
    (let [res (db/get-user-assessment-series db {:user-ids user-ids})]
      (into {} (map #(vector (:user-id %) (:assessment-series-id %))) res))))

(defn- groups-assessment-series-id
  [db group-ids]
  (when group-ids
    (let [res (db/get-group-assessment-series db {:group-ids group-ids})]
      (into {} (map #(vector (:group-id %) (:assessment-series-id %))) res))))

(defn- activated-participant-administrations
  [db date-min date-max hour]
  (db/get-activated-participant-administrations db {:date-min date-min
                                                    :date-max date-max
                                                    :hour     hour}))

(defn- activated-group-administrations
  [db date-min date-max hour]
  (db/get-activated-group-administrations db {:date-min date-min
                                              :date-max date-max
                                              :hour     hour}))

(defn- participant-administrations-by-user+assessment+series
  [db user+assessments+series]
  (db/get-participant-administrations-by-user+assessment
    db
    {:user-ids+assessment-ids user+assessments+series}))

(defn group-administrations-by-group+assessment+series
  [db groups+assessments+series]
  (db/get-group-administrations-by-group+assessment db {:group-ids+assessment-ids groups+assessments+series}))

(defn assessments
  [assessment-ids]
  (when assessment-ids
    (->> (db/get-assessments {:assessment-ids assessment-ids})
         (map #(vector (:assessment-id %) %))
         (into {}))))

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
  "Returns list of potentially active assessments for users
  {:user-id 653692,
   :group-id 653637,
   :participant-administration-id nil,
   :group-administration-id 653640,
   :assessment-id 653636,
   :assessment-index 1,
   ::remind-type :activation}"
  [db now tz]
  (let [hour                        (t/hour (t/to-time-zone now tz))
        date-min                    (today-midnight now tz)
        date-max                    (today-last-second now tz)
        participant-administrations (activated-participant-administrations db date-min date-max hour)
        group-administrations       (activated-group-administrations db date-min date-max hour)]
    (->> (concat participant-administrations
                 group-administrations)
         (map #(assoc % ::remind-type ::activation)))))

(defn participant-administrations-from-potential-assessments
  "Returns all administrations for user-assessment combo,
  grouped by [user-id assessment-id"
  [db potential-assessments]
  (let [assessment-series       (->> potential-assessments
                                     (map :user-id)
                                     (users-assessment-series-id db))
        user+assessments+series (->> potential-assessments
                                     (map #(vector (:user-id %)
                                                   (:assessment-id %)
                                                   (get assessment-series (:user-id %))))
                                     (into #{}))
        administrations         (participant-administrations-by-user+assessment+series
                                  db user+assessments+series)]
    (group-by #(vector (:user-id %) (:assessment-id %)) administrations)))

(defn group-administrations-from-potential-assessments
  "Returns all administrations for group-assessment combo,
  grouped by [group-id assessment-id"
  [db potential-assessments]
  (let [potential-assessments     (filter :group-id potential-assessments)
        assessment-series         (->> potential-assessments
                                       (map :group-id)
                                       (groups-assessment-series-id db))
        groups+assessments+series (->> potential-assessments
                                       (map #(vector (:group-id %)
                                                     (:assessment-id %)
                                                     (get assessment-series (:group-id %))))
                                       (into #{}))
        administrations           (group-administrations-by-group+assessment+series
                                    db groups+assessments+series)]
    (group-by #(vector (:group-id %) (:assessment-id %)) administrations)))

;; --------------
;; HOW THIS WORKS
;; --------------
;; Premise:
;;   - I can't manage to figure out if it is possible to execute a single SQL statement
;;     to get all active group and participant administrations.
;;   - All administrations belonging to an assessment are needed to determine if one
;;     administration is ongoing (because of manual repetition - but interval repetition
;;     should also be modified to only allow one administrations to be active
;;     (and manual should too!)
;;   - Ongoing assessment may be in wrong assessment series and therefore need to be
;;     qualified.
;;
;; Steps:
;;   1. Get potentially active participant and group administrations.
;;   2. Get ALL p and g administrations of the assessments of these p and g administrations
;;   3. Combine these administrations into a format acceptable to the assessment-ongoing functions
;;   4. Check if administrations are ongoing
;;

;; TESTS THAT NEED TO BE WRITTEN
;;   - Assessment from other project

(defn- merge-participant-group-administrations
  [user-id participant-administrations group-administrations]
  (->> (concat participant-administrations group-administrations) ;; From https://stackoverflow.com/a/20808420
       (sort-by :assessment-index)
       (partition-by :assessment-index)
       (map (partial apply merge))
       (map #(assoc % :active? (and (or (:group-administration-active? %) true)
                                    (or (:participant-administration-active? %) true))
                      :user-id user-id))))

(defn- ongoing-from-potentials
  "Returns list of ALL ongoing assessments based on list of potentials.
  Note, ALL means that ongoing assessment that are not part of potentials may be returned"
  [db now potentials]
  (let [user-groups                         (->> potentials
                                                 (map #(vector (:user-id %) (:group-id %)))
                                                 (into {}))
        participant-administrations-grouped (participant-administrations-from-potential-assessments db potentials)
        group-administrations-grouped       (group-administrations-from-potential-assessments db potentials)
        assessments'                        (assessments (->> potentials
                                                              (map :assessment-id)
                                                              (into #{})))
        merged-by-user+assessment           (->> potentials
                                                 (map #(vector (:user-id %) (:assessment-id %)))
                                                 (map (fn [[user-id assessment-id]]
                                                        (let [group-id                    (get user-groups user-id)
                                                              participant-administrations (get participant-administrations-grouped [user-id assessment-id])
                                                              group-administrations       (when group-id
                                                                                            (get group-administrations-grouped [group-id assessment-id]))
                                                              merged-administrations      (merge-participant-group-administrations
                                                                                            user-id
                                                                                            participant-administrations
                                                                                            group-administrations)]
                                                          [[user-id assessment-id] merged-administrations])))
                                                 (into {}))
        ongoing-assessments                 (mapv (fn [[[_ assessment-id] administrations]]
                                                    (assessment-ongoing/ongoing-administrations
                                                      now administrations (get assessments' assessment-id)))
                                                  merged-by-user+assessment)]
    (flatten ongoing-assessments)))

(defn ongoing-reminder-assessments
  [db now potentials]
  (when (seq potentials)
    (let [ongoing-assessments                (ongoing-from-potentials db now potentials)
          potential-by+user+assessment+index (->> potentials
                                                  (map #(vector [(:user-id %) (:assessment-id %) (:assessment-index %)] %))
                                                  (into {}))
          filtered-ongoing-potentials        (->> ongoing-assessments
                                                  (map (fn [ongoing]
                                                         (let [potential (get potential-by+user+assessment+index
                                                                              [(:user-id ongoing)
                                                                               (:assessment-id ongoing)
                                                                               (:assessment-index ongoing)])]
                                                           (assoc ongoing ::remind-type (::remind-type potential)))))
                                                  (filter ::remind-type))]
      filtered-ongoing-potentials)))

(defn activation-reminders*
  [db now tz]
  (let [potentials (potential-activation-reminders db now tz)]
    (ongoing-reminder-assessments db now potentials)))

(def tz (t/time-zone-for-id "Asia/Tokyo"))
