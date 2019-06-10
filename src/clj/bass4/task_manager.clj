(ns bass4.task-manager
  (:require [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [bass4.db.core :as db]
            [clojure.java.jdbc :as jdbc]
            [bass4.utils :as utils])
  (:import [java.util.concurrent Executors TimeUnit ScheduledExecutorService ScheduledThreadPoolExecutor$ScheduledFutureTask]))

(defonce schedule-pool ^ScheduledExecutorService (Executors/newScheduledThreadPool 4))
(defonce task-pool (Executors/newFixedThreadPool 8))

;; TODO: Shutdown and restart tasks

;; ----------------
;;     TASK DB
;; ----------------

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

;; ----------------
;;   TASK RUNNER
;; ----------------

(defonce tasks-running (atom {}))

(defn running?!
  [task-name db-name]
  (let [[new old] (swap-vals! tasks-running (fn [a]
                                              (if (contains? a [task-name db-name])
                                                a
                                                (assoc a [task-name db-name] true))))]
    (= new old)))

(defn finished!
  [task db-name]
  (swap! tasks-running #(dissoc % [task db-name])))

(defn run-db-task!
  [db-name task task-name]
  (if (running?! task-name db-name)
    (-> (open-db-entry! db-name task-name (t/now))
        (close-db-entry! (t/now) {} "already running"))
    (let [db-id (open-db-entry! db-name task-name (t/now))
          res   (try (task db-name (t/now))
                     (catch Exception e
                       {:exception e}))]
      (close-db-entry! db-id (t/now) res "finished")
      (finished! task-name db-name))))


;; ----------------
;;  TASK SCHEDULER
;; ----------------

(defonce tasks (atom {}))

(defn run-db-tasks!
  [task task-name]
  (doseq [db-name (keys db/db-connections)]
    (let [[task-id _] (get @tasks task-name)]
      (log/debug "Running task " task-name "with id" task-id "for" db-name))
    (.execute task-pool
              #(run-db-task! db-name task task-name))))

(defn cancel-task*!
  [task-name]
  (let [[task-id handle] (get @tasks task-name)]
    (log/info "Cancelling task" task-name "with id" task-id)
    (.cancel ^ScheduledThreadPoolExecutor$ScheduledFutureTask handle false)
    (swap! tasks #(dissoc % task-name))))

(defn cancel-all!
  []
  (doseq [[task-name [task-id handle]] @tasks]
    (.cancel ^ScheduledThreadPoolExecutor$ScheduledFutureTask handle false)
    (swap! tasks #(dissoc % task-name))))

(defonce task-counter (atom 0))

(defn add-task*!
  [task task-name & scheduling]
  (when (contains? @tasks task-name)
    (cancel-task*! task-name))
  (let [task-id (swap! task-counter inc)]
    (log/info "Adding task" task-name "with id" task-id)
    (let [[minutes-left interval] (case (first scheduling)
                                    :hourly
                                    [(- 60 (t/minute (t/now)))
                                     60]

                                    :by-minute
                                    [0
                                     (second scheduling)])
          handle (.scheduleAtFixedRate schedule-pool
                                       #(run-db-tasks! task task-name)
                                       (long minutes-left)
                                       interval
                                       TimeUnit/SECONDS)]
      (swap! tasks #(assoc % task-name [task-id handle])))))


;; ----------------
;;   TASK ADDER
;; ----------------

(defmacro add-hourly-task!
  [task]
  (let [task-name# (str *ns* "/" (if (symbol? task)
                                   (name task)
                                   (str task)))]
    `(add-task*! ~task ~task-name# :hourly)))

(defmacro add-by-minute-task!
  [task interval]
  (let [task-name# (str *ns* "/" (if (symbol? task)
                                   (name task)
                                   (str task)))]
    `(add-task*! ~task ~task-name# :by-minute ~interval)))


;; ----------------
;;   TEST TASKS
;; ----------------

(defn task-tester
  [db-name now]
  (log/debug "Running task for" db-name)
  {:cycles 100})

(defn task-tester2
  [db-name now]
  (log/debug "Running task for" db-name)
  {:cycles 100})