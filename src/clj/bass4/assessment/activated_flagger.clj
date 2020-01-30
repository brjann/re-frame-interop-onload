(ns bass4.assessment.activated-flagger
  (:require [clj-time.core :as t]
            [clojure.core.async :refer [put!]]
            [bass4.db.core :as db]
            [bass4.assessment.reminder :as assessment-reminder]
            [bass4.services.bass :as bass]
            [bass4.assessment.create-missing :as missing]))

(defn- db-participant-administrations
  [db date]
  (db/get-flagging-late-flag-participant-administrations db {:date           date
                                                             :oldest-allowed (t/minus date (t/days oldest-allowed))
                                                             :issuer         flag-issuer}))

(defn- db-group-administrations
  [db date]
  (db/get-flagging-late-flag-group-administrations db {:date           date
                                                       :oldest-allowed (t/minus date (t/days oldest-allowed))
                                                       :issuer         flag-issuer}))
