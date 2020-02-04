(ns bass4.task.scheduler
  (:require [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [clojure.repl :as repl]
            [bass4.task.runner :as task-runner]
            [bass4.db-config :as db-config]
            [bass4.db.core :as db]
            [bass4.utils :as utils])
  (:import [java.util.concurrent Executors TimeUnit ScheduledExecutorService ScheduledThreadPoolExecutor$ScheduledFutureTask]
           (clojure.lang Var)))

(defonce schedule-pool ^ScheduledExecutorService (Executors/newScheduledThreadPool 10))
(defonce task-counter (atom 0))
(defonce task-handlers (atom {}))
(defonce task-list (atom {}))

(defn cancel-task-for-dbs
  [task-name db-tasks]
  (doseq [[handle db-name task-id] db-tasks]
    (log/info "Cancelling task" task-name "with id" task-id "for db" db-name)
    (.cancel ^ScheduledThreadPoolExecutor$ScheduledFutureTask handle false)))

(defn cancel-task!
  [task-name]
  (let [[old new] (swap-vals! task-handlers #(dissoc % task-name))]
    (when (and (contains? old task-name) (not (contains? new task-name)))
      (cancel-task-for-dbs task-name (get old task-name)))))

(defn cancel-all!
  []
  (let [[tasks* _] (reset-vals! task-handlers {})]
    (doseq [[task-name db-tasks] tasks*]
      (cancel-task-for-dbs task-name db-tasks))))

(defn interval-params
  [scheduling]
  (case (first scheduling)
    ::hourly
    [(- 60 (t/minute (t/now)))
     60
     TimeUnit/MINUTES]

    ::by-minute
    [0
     (second scheduling)
     TimeUnit/MINUTES]

    ::by-millisecond
    [0
     (second scheduling)
     TimeUnit/MILLISECONDS]))

(defn task-dbs
  []
  (remove #(db-config/db-setting* % [:no-tasks?] false) (keys db/db-connections)))

(defn schedule-db-task*!
  [task task-name scheduling]
  (doseq [db-name (task-dbs)]
    (let [task-id (swap! task-counter inc)]
      (log/info "Adding task" task-name "with id" task-id "for" db-name "")
      (let [[minutes-left interval time-unit] (interval-params scheduling)
            handle (.scheduleAtFixedRate schedule-pool
                                         (bound-fn*
                                           (fn []
                                             (let [db        @(get db/db-connections db-name)
                                                   db-config (get db-config/local-configs db-name)]
                                               (log/debug "Running task" task-name "with id" task-id "for" db-name)
                                               (task-runner/run-db-task! db (t/now) db-name db-config task task-name task-id))))
                                         (long minutes-left)
                                         interval
                                         time-unit)]
        (utils/swap-key! task-handlers task-name #(conj % [handle db-name task-id]) [])
        (swap! task-list #(assoc % task-name [task scheduling]))
        task-id))))

(defn schedule-db-task!
  [task & scheduling]
  (assert (or (instance? Var task)
              (fn? task)))
  (let [task-name (if (instance? Var task)
                    (subs (str task) 2)
                    (repl/demunge (str task)))]
    (when (contains? @task-handlers task-name)
      (cancel-task! task-name))
    (schedule-db-task*! task task-name scheduling)))

(defn- reschedule-db-tasks!
  []
  (doseq [[task-name [task scheduling]] @task-list]
    (cancel-task! task-name)
    (schedule-db-task*! task task-name scheduling)))

(defn db-watcher
  [_ _ old-state, new-state]
  (let [old-keys (keys old-state)
        new-keys (keys new-state)]
    (when (not= old-keys new-keys)
      (reschedule-db-tasks!))))

(add-watch db/connected-dbs ::db-watcher db-watcher)