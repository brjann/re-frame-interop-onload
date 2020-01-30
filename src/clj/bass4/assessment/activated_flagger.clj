(ns bass4.assessment.activated-flagger
  (:require [clj-time.core :as t]
            [clojure.core.async :refer [put!]]
            [bass4.db.core :as db]
            [bass4.assessment.reminder :as assessment-reminder]
            [bass4.services.bass :as bass]
            [bass4.assessment.create-missing :as missing]))

(def flag-issuer "tActivatedAdministrationsFlagger")
(def oldest-allowed 100)

(defn date-intervals
  [now tz]
  (let [midnight (bass/local-midnight now tz)]
    [(-> midnight
         (t/minus (t/days oldest-allowed)))
     (-> midnight
         (t/plus (t/days 1))
         (t/minus (t/seconds 1)))]))

(defn- db-participant-administrations
  [db now tz]
  (let [[date-min date-max] (date-intervals now tz)]
    (db/get-flagging-activated-participant-administrations db {:date-max date-max
                                                               :date-min date-min
                                                               :issuer   flag-issuer})))

(defn- db-group-administrations
  [db now tz]
  (let [[date-min date-max] (date-intervals now tz)]
    (db/get-flagging-activated-group-administrations db {:date-max date-max
                                                         :date-min date-min
                                                         :issuer   flag-issuer})))
