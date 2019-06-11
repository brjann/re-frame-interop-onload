(ns bass4.task.scheduler
  (:require [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [bass4.task.runner :as runner])
  (:import [java.util.concurrent Executors TimeUnit ScheduledExecutorService ScheduledThreadPoolExecutor$ScheduledFutureTask]))

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

(defonce task-counter (atom 0))

(defn schedule!
  [task task-name & scheduling]
  (when (contains? @tasks task-name)
    (cancel-task*! task-name))
  (let [task-id (swap! task-counter inc)]
    (log/info "Adding task" task-name "with id" task-id)
    (let [[minutes-left interval time-unit] (case (first scheduling)
                                              :hourly
                                              [(- 60 (t/minute (t/now)))
                                               60
                                               TimeUnit/MINUTES]

                                              :by-minute
                                              [0
                                               (second scheduling)
                                               TimeUnit/MINUTES]

                                              :by-millisecond
                                              [0
                                               (second scheduling)
                                               TimeUnit/MILLISECONDS])
          handle (.scheduleAtFixedRate schedule-pool
                                       (bound-fn*
                                         #(runner/run-task-for-dbs! task task-name task-id))
                                       (long minutes-left)
                                       interval
                                       time-unit)]
      (swap! tasks #(assoc % task-name [task-id handle])))))