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
  [db now tz]
  (let [hour                        (t/hour (t/to-time-zone now tz))
        date-min                    (today-midnight now tz)
        date-max                    (today-last-second now tz)
        participant-administrations (activated-participant-administrations db date-min date-max hour)
        group-administrations       (activated-group-administrations db date-min date-max hour)]
    (concat participant-administrations
            group-administrations)))

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

(defn administrations-activated-today*
  [db now tz]
  (let [potential (potential-activation-reminders db now tz)]
    (when (seq potential)
      (let [user-groups                         (->> potential
                                                     (map #(vector (:user-id %) (:group-id %)))
                                                     (into {}))
            participant-administrations-grouped (participant-administrations-from-potential-assessments db potential)
            group-administrations-grouped       (group-administrations-from-potential-assessments db potential)
            assessments'                        (assessments (->> potential
                                                                  (map :assessment-id)
                                                                  (into #{})))
            merged-by-user+assessment           (->> potential
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
            ongoing                             (mapv (fn [[[_ assessment-id] administrations]]
                                                        (assessment-ongoing/ongoing-administrations
                                                          now administrations (get assessments' assessment-id)))
                                                      merged-by-user+assessment)]
        ongoing))))

(defn administrations-activated-today
  []
  (administrations-activated-today* db/*db* (t/now) tz))