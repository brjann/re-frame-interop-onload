(ns bass4.task.runner
  (:require [bass4.task.log :as task-log]
            [clj-time.core :as t]
            [bass4.db.core :as db]
            [clojure.tools.logging :as log]
            [bass4.db-config :as db-config])
  (:import [java.util.concurrent Executors ScheduledExecutorService]))


(defonce task-pool ^ScheduledExecutorService (Executors/newFixedThreadPool 8))

(defonce tasks-running (atom #{}))

(defn- running?!
  [task-id]
  (let [[new old] (swap-vals! tasks-running #(conj % task-id))]
    (= new old)))

(defn- finished!
  [task-id]
  (swap! tasks-running #(disj % task-id)))

(defn run-db-task!
  [db db-now db-name db-config task task-name task-id]
  (if (running?! task-id)
    (-> (task-log/open-db-entry! db-name task-name (t/now))
        (task-log/close-db-entry! (t/now) {} "already running"))
    (binding [db/*db*                  nil
              db-config/*local-config* nil]
      (let [db-id (task-log/open-db-entry! db-name task-name (t/now))
            res   (try (task
                         db
                         db-config
                         db-now)
                       (catch Exception e
                         (log/debug e)
                         {:exception e}))]
        (task-log/close-db-entry! db-id (t/now) res "finished")
        (finished! task-id)
        db-id))))