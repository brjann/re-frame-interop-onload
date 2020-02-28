(ns bass4.task.runner
  (:require [bass4.task.log :as task-log]
            [clj-time.core :as t]
            [bass4.db.core :as db]
            [clojure.tools.logging :as log]
            [bass4.now :as now]
            [bass4.clients.core :as clients]))

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
  (if (and task-id (running?! task-id))
    (-> (task-log/open-db-entry! db-name task-name (now/now))
        (task-log/close-db-entry! (now/now) {} "already running"))
    (binding [db/*db*                 nil
              clients/*client-config* nil]
      (let [db-id (task-log/open-db-entry! db-name task-name (now/now))
            res   (try (task
                         db
                         db-config
                         db-now)
                       (catch Exception e
                         (log/error e)
                         {:exception e}))]
        (task-log/close-db-entry! db-id (now/now) res "finished")
        (finished! task-id)
        db-id))))