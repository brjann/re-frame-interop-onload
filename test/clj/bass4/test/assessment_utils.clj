(ns bass4.test.assessment-utils
  (:require [clj-time.core :as t]
            [bass4.db.core :refer [*db*] :as db]
            [bass4.test.core :refer :all]
            [clojure.test :refer :all]
            [bass4.utils :as utils]
            [clojure.java.jdbc :as jdbc]
            [clj-time.coerce :as tc]
            [bass4.assessment.reminder :as assessment-reminder]))

(use-fixtures
  :once
  test-fixtures)

(def project-id 653627)

(def ass-group-single-2-3 653630)
(def ass-group-weekly-3-4 653631)
(def ass-individual-single-0 653632)
(def ass-individual-weekly-no-remind 653633)
(def ass-individual-manual-5-10 653634)
(def ass-clinician 654215)
(def ass-hour8-2-20 654411)

(def assessment-ids [ass-group-single-2-3
                     ass-group-weekly-3-4
                     ass-individual-single-0
                     ass-individual-weekly-no-remind
                     ass-individual-manual-5-10
                     ass-clinician
                     ass-hour8-2-20])

(defn create-group!
  []
  (:objectid (db/create-bass-object! {:class-name    "cGroup"
                                      :parent-id     project-id
                                      :property-name "Groups"})))

(defn create-group-administration!
  [group-id assessment-id assessment-index & [properties]]
  (let [administration-id (:objectid (db/create-bass-object! {:class-name    "cGroupAdministration"
                                                              :parent-id     group-id
                                                              :property-name "Administrations"}))]
    (when properties
      (db/update-object-properties! {:table-name "c_groupadministration"
                                     :object-id  administration-id
                                     :updates    (merge {:assessment      assessment-id
                                                         :assessmentindex assessment-index
                                                         :active          1}
                                                        properties)}))
    administration-id))

(defn create-participant-administration!
  [user-id assessment-id assessment-index & [properties]]
  (let [administration-id (:objectid (db/create-bass-object! {:class-name    "cParticipantAdministration"
                                                              :parent-id     user-id
                                                              :property-name "Administrations"}))]
    (db/update-object-properties! {:table-name "c_participantadministration"
                                   :object-id  administration-id
                                   :updates    (merge {:assessment      assessment-id
                                                       :assessmentindex assessment-index
                                                       :active          1
                                                       :deleted         0}
                                                      properties)})
    administration-id))

(defn clear-administrations!
  []
  (let [qmarks (apply str (interpose \, (repeat (count assessment-ids) \?)))]
    (jdbc/execute! db/*db*
                   (cons (str "UPDATE c_participantadministration SET Date = 0 WHERE assessment IN (" qmarks ")")
                         assessment-ids))
    (jdbc/execute! db/*db*
                   (cons (str "UPDATE c_groupadministration SET Date = 0 WHERE assessment IN (" qmarks ")")
                         assessment-ids))))

(def ^:dynamic *now*)
(def ^:dynamic *tz*)

(defn midnight-joda
  [now]
  (-> now
      (t/to-time-zone *tz*)
      (t/with-time-at-start-of-day)))

(defn midnight
  [now]
  (-> now
      (t/to-time-zone *tz*)
      (t/with-time-at-start-of-day)
      (utils/to-unix)))

(defn midnight+d
  [plus-days now]
  (utils/to-unix (t/plus (-> now
                             (t/to-time-zone *tz*)
                             (t/with-time-at-start-of-day))
                         (t/days plus-days))))

(defn random-date
  []
  (-> (long (rand 2147483647))
      (tc/from-epoch)))

(defn random-date-tz-fixture
  [f]
  (clear-administrations!)
  (let [tz  (t/time-zone-for-id (rand-nth (seq (t/available-ids))))
        now (random-date)]
    (binding [*tz*  tz
              *now* now]
      (f))))

(defn reminders
  [now]
  (->> (assessment-reminder/reminders* db/*db* now *tz*)
       (map #(vector
               (:user-id %)
               (some? (:participant-administration-id %))
               (:assessment-id %)
               (:assessment-index %)
               (::assessment-reminder/remind-type %)))
       (into #{})))