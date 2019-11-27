(ns bass4.assessment.flagger
  (:require [clj-time.core :as t]
            [bass4.db.core :as db]))

(defn- db-late-flag-participant-administrations
  [db date]
  (db/get-late-flag-participant-administrations db {:date           date
                                                    :oldest-allowed (t/minus date (t/days 100))}))