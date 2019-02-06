(ns bass4.test.sql-race
  (:require [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [clojure.core.async :refer [thread]]
            [bass4.db.core :as db]))

(defn test-new-round-id []
  (let [res (atom #{})]
    (jdbc/execute! db/*db* "UPDATE `c_project` SET `AssessmentRoundIdNext`=NULL")
    (jdbc/execute! db/*db* "SET @debug_sleep = 0.1")
    (dotimes [x 100]
      (thread
        (do
          ; If FOR UPDATE is removed from SELECT statement,
          ; then non-unique IDs are created.
          (let [{:keys [round-id]} (db/get-new-round-id!)]
            (swap! res conj round-id))
          (println x))))
    res))

(defn reset-debug-sleep! []
  (jdbc/execute! db/*db* "SET @debug_sleep = 0"))