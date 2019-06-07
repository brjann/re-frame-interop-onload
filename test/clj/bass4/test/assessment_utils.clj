(ns bass4.test.assessment-utils
  (:require [clj-time.core :as t]
            [bass4.db.core :refer [*db*] :as db]
            [clojure.core.async :refer [chan alts!! timeout]]
            [bass4.test.core :refer :all]
            [clojure.test :refer :all]
            [bass4.utils :as utils]
            [clojure.java.jdbc :as jdbc]
            [clj-time.coerce :as tc]
            [bass4.assessment.reminder :as assessment-reminder]
            [clojure.tools.logging :as log]
            [bass4.assessment.create-missing :as missing]
            [clojure.pprint :as pprint]))

(use-fixtures
  :once
  test-fixtures)

(def project-id 653627)

(def ass-G-s-2-3-p0 653630)
(def ass-G-week-e+s-3-4-p10 653631)
(def ass-I-s-0-p100-message 653632)
(def ass-I-week-noremind 653633)
(def ass-I-manual-s-5-10-q 653634)
(def ass-I-clinician 654215)
(def ass-I-hour8-2-20 654411)

(def assessment-ids [ass-G-s-2-3-p0
                     ass-G-week-e+s-3-4-p10
                     ass-I-s-0-p100-message
                     ass-I-week-noremind
                     ass-I-manual-s-5-10-q
                     ass-I-clinician
                     ass-I-hour8-2-20])

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
  (->> (assessment-reminder/reminders db/*db* now *tz*)
       (map #(vector
               (:user-id %)
               (some? (:participant-administration-id %))
               (:assessment-id %)
               (:assessment-index %)
               (::assessment-reminder/remind-type %)
               (::assessment-reminder/remind-number %)))
       (into #{})))

(defn remind!
  [now]
  (assessment-reminder/remind! db/*db* now *tz*))

(defn remind!-created
  [now]
  (binding [missing/*create-count-chan* (chan)]
    (remind! now)
    (let [[create-count _] (alts!! [missing/*create-count-chan* (timeout 1000)])]
      create-count)))

(defn- message-vec
  [message type]
  (when message
    [(:user-id message) (:assessment-id message) type]))

(defn messages
  [now]
  (->> (assessment-reminder/reminders db/*db* now *tz*)
       (assessment-reminder/remind-messages)
       (map #(vector
               (:user-id %)
               (:assessment-id %)
               (::assessment-reminder/message-type %)))
       (into #{})))