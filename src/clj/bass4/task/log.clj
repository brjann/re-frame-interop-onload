(ns bass4.task.log
  (:require [clojure.java.jdbc :as jdbc]
            [bass4.utils :as utils]
            [bass4.db.core :as db]))


(defn open-db-entry!
  [db-name task-name now]
  (jdbc/with-db-connection [db db/db-common]
    (let [unix-now (utils/to-unix now)]
      (jdbc/execute! db ["INSERT INTO common_log_tasks (`DB`, `TaskName`, `StillRunning`, `StartTime`, `LastActivity`) VALUES(?, ?, 1, ?, ?)"
                         (name db-name)
                         (str "clj-" task-name)
                         unix-now
                         unix-now]))
    (-> (jdbc/query db "SELECT LAST_INSERT_ID() AS id")
        (first)
        (:id))))

(defn close-db-entry!
  [db-id now response termination-reason]
  (jdbc/with-db-connection [db db/db-common]
    (let [unix-now (utils/to-unix now)]
      (jdbc/execute! db [(str "UPDATE common_log_tasks "
                              "SET `StillRunning` = 0, `TerminationReason` = ?, "
                              "`EndTime` = ?, `InitResponse` = ?, `Cycles` = ?, `Error` = ?, `Errors` = ? "
                              "WHERE ExecId = ?")
                         termination-reason
                         unix-now
                         (if (empty? response) 0 1)
                         (or (:cycles response) 0)
                         (if (:exception response) 1 0)
                         (if (:exception response) (str (:exception response)) "")
                         db-id]))))
