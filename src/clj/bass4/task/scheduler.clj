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

(defonce schedule-pool ^ScheduledExecutorService (Executors/newScheduledThreadPool 4))

(defonce tasks (atom {}))

(defn cancel-task*!
  [task-name]
  (let [[task-id handle] (get @tasks task-name)]
    (if handle
      (do
        (log/info "Cancelling task" task-name "with id" task-id)
        (.cancel ^ScheduledThreadPoolExecutor$ScheduledFutureTask handle false)
        (swap! tasks #(dissoc % task-name))
        true)
      false)))

(defn cancel-all!
  []
  (doseq [[task-name [_ handle]] @tasks]
    (.cancel ^ScheduledThreadPoolExecutor$ScheduledFutureTask handle false)
    (swap! tasks #(dissoc % task-name))))

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

(defonce task-counter (atom 0))

(defn schedule-db-task!
  [task & scheduling]
  (assert (or (instance? Var task)
              (fn? task)))
  (let [task-name (if (instance? Var task)
                    (subs (str task) 2)
                    (repl/demunge (str task)))]
    (when (contains? @tasks task-name)
      (cancel-task*! task-name))
    (doseq [db-name (task-dbs)]
      (let [task-id (swap! task-counter inc)]
        (let [[minutes-left interval time-unit] (interval-params scheduling)
              handle (.scheduleAtFixedRate schedule-pool
                                           (bound-fn*
                                             (fn []
                                               (let [db        @(get db/db-connections db-name)
                                                     db-config (get db-config/local-configs db-name)]
                                                 (log/debug "Running task " task-name "with id" task-id "for" db-name)
                                                 (task-runner/run-db-task! db (t/now) db-name db-config task task-name task-id))))
                                           (long minutes-left)
                                           interval
                                           time-unit)]
          (log/info "Adding task" task-name "with id" task-id "for" db-name "")
          (utils/swap-key! tasks task-name #(conj % [handle db-name task-id]) [[handle db-name task-id]])
          task-id)))))