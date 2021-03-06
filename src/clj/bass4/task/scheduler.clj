(ns bass4.task.scheduler
  (:require [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [clojure.repl :as repl]
            [bass4.task.runner :as task-runner]
            [bass4.db.core :as db]
            [bass4.utils :as utils]
            [mount-up.core :as mount-up]
            [bass4.clients.core :as clients]
            [bass4.now :as now]
            [bass4.external-messages.email-sender :as email])
  (:import [java.util.concurrent Executors TimeUnit ScheduledExecutorService ScheduledThreadPoolExecutor$ScheduledFutureTask]
           (clojure.lang Var)))

(defonce schedule-pool ^ScheduledExecutorService (Executors/newScheduledThreadPool 10))
(defonce task-counter (atom 0))
(defonce task-handlers (atom {}))
(defonce task-list (atom {}))
(defonce db-tracker (atom nil))

(defn cancel-task-for-dbs!
  [task-name db-tasks]
  (doseq [[handle db-name task-id] db-tasks]
    (log/info "Cancelling task" task-name "with id" task-id "for db" db-name)
    (.cancel ^ScheduledThreadPoolExecutor$ScheduledFutureTask handle false)))

(defn cancel-task!
  [task-name]
  (let [[old new] (swap-vals! task-handlers #(dissoc % task-name))]
    (when (and (contains? old task-name) (not (contains? new task-name)))
      (cancel-task-for-dbs! task-name (get old task-name)))))

(defn cancel-all!
  []
  (let [[tasks* _] (reset-vals! task-handlers {})]
    (doseq [[task-name db-tasks] tasks*]
      (cancel-task-for-dbs! task-name db-tasks))))

(defn interval-params
  [scheduling tz]
  (case (first scheduling)
    ::hourly
    [(- 60 (t/minute (now/now)))
     60
     TimeUnit/MINUTES]

    ::by-minute
    [(second scheduling)
     (second scheduling)
     TimeUnit/MINUTES]

    ::by-millisecond
    [(second scheduling)
     (second scheduling)
     TimeUnit/MILLISECONDS]

    ::daily-at
    (let [wait (let [current-tz-hour (t/hour (t/to-time-zone (now/now) tz))
                     target-tz-hour  (second scheduling)
                     current-minute  (t/minute (now/now))
                     minutes-until   (-> (- target-tz-hour current-tz-hour)
                                         (* 60)
                                         (- current-minute))]
                 (if (> target-tz-hour current-tz-hour)
                   minutes-until
                   (+ minutes-until (* 24 60))))]
      [wait
       (* 24 60)
       TimeUnit/MINUTES])))

(defn task-dbs
  []
  (remove #(clients/client-setting* % [:no-tasks?] false) (keys clients/client-db-connections)))

(defn- schedule-db-task*!
  [task task-name scheduling]
  (doseq [db-name (task-dbs)]
    (let [task-id   (swap! task-counter inc)
          db-config (get clients/client-configs db-name)
          tz        (-> (:timezone db-config "Europe/Stockholm")
                        (t/time-zone-for-id))]
      (let [[time-left interval time-unit] (interval-params scheduling tz)
            handle (.scheduleAtFixedRate schedule-pool
                                         (bound-fn*
                                           (fn []
                                             (try
                                               (let [db        @(get clients/client-db-connections db-name)
                                                     db-config (get clients/client-configs db-name)]
                                                 (task-runner/run-db-task! db (now/now) db-name db-config task task-name task-id))
                                               (catch Throwable e
                                                 (log/error "UNCAUGHT TASK EXCEPTION")
                                                 (log/error "DB:" db-name "Task:" task-name)
                                                 (log/error e)
                                                 (email/send-error-email! "Task handler" "Uncaught exception in task handler. See log for details.")))))
                                         (long time-left)
                                         interval
                                         time-unit)]
        (log/info "Adding task"
                  task-name
                  "with id" task-id
                  "for" db-name
                  "Next run in" time-left (str time-unit)
                  "and then every" interval)
        (utils/swap-key! task-handlers task-name #(conj % [handle db-name task-id]) [])
        (swap! task-list #(assoc % task-name [task scheduling]))))))

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
  "Watches the DBs for changes in connection. Reschedules all tasks if
  changes are detected"
  [{:keys [name]}]
  (if (= name (str #'bass4.clients.core/client-db-connections))
    (if (map? clients/client-db-connections)
      (let [new-dbs (keys clients/client-db-connections)
            old-dbs @db-tracker]
        (when-not (or (nil? @db-tracker) (= new-dbs old-dbs))
          (log/info "DB connections change detected, rescheduling tasks.")
          (reschedule-db-tasks!))
        (reset! db-tracker new-dbs))
      (log/error "CANNOT RESOLVE DBS"))))

(mount-up/on-up ::db-watcher db-watcher :after)

(comment
  "This function can be used later to manage scheduled tasks"
  (map #(.getDelay % TimeUnit/SECONDS) (.getQueue schedule-pool)))