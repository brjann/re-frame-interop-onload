(ns bass4.cleaner.tables
  (:require [mount.core :refer [defstate]]
            [bass4.task.scheduler :as task-scheduler]
            [clojure.java.jdbc :as jdbc]
            [bass4.utils :as utils]
            [clj-time.core :as t]
            [bass4.db.core :as db]))

;; TODO: Move common query to separate global task
(defn delete-temp-tables-task
  [db _ now]
  (let [i24hrs (- (utils/to-unix now) (* 24 60 60))
        t24hrs (t/minus now (t/hours 24))
        t7days (t/minus now (t/days 7))
        counts [(jdbc/execute! db ["DELETE FROM objectactions WHERE `time` < ?" i24hrs])
                (jdbc/execute! db ["DELETE FROM posts WHERE `time` < ?" i24hrs])
                (jdbc/execute! db ["DELETE FROM sessions WHERE `LastActivity` < ?" i24hrs])
                (jdbc/execute! db ["DELETE FROM singleobjectaccesses WHERE `time` < ?" i24hrs])
                (jdbc/execute! db ["DELETE FROM assessment_rounds WHERE `Time` < ?" t24hrs])
                (jdbc/execute! db/db-common ["DELETE FROM common_log_failed_logins where `Time` < ?" t7days])
                (jdbc/execute! db/db-common ["DELETE FROM passwords where `valid-until` < ?" t7days])]]
    {:cycles (->> counts
                  (map first)
                  (reduce +))}))

(defstate delete-temp-tables-task-starter
  :start (task-scheduler/schedule-db-task! #'delete-temp-tables-task
                                           ::task-scheduler/daily-at 3))