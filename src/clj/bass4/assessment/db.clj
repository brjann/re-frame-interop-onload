(ns bass4.assessment.db
  (:require [bass4.db.core :as db]
            [bass4.clients.time :as client-time]
            [clj-time.core :as t]
            [clojure.tools.logging :as log]))

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