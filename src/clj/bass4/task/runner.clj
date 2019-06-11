(ns bass4.task.runner
  (:require [bass4.task.log :as task-log]
            [clj-time.core :as t]
            [bass4.db.core :as db]
            [clojure.tools.logging :as log])
  (:import [java.util.concurrent Executors ScheduledExecutorService]))


(defonce task-pool ^ScheduledExecutorService (Executors/newFixedThreadPool 8))

(defonce tasks-running (atom {}))

(defn- running?!
  [task-name db-name]
  (let [[new old] (swap-vals! tasks-running (fn [a]
                                              ;; TODO: Could be simplified to assoc
                                              (if (contains? a [task-name db-name])
                                                a
                                                (assoc a [task-name db-name] true))))]
    (= new old)))

(defn- finished!
  [task db-name]
  (swap! tasks-running #(dissoc % [task db-name])))

(defn- run-db-task!
  [db-name task task-name]
  (if (running?! task-name db-name)
    (-> (task-log/open-db-entry! db-name task-name (t/now))
        (task-log/close-db-entry! (t/now) {} "already running"))
    (let [db-id (task-log/open-db-entry! db-name task-name (t/now))
          res   (try (task db-name (t/now))
                     (catch Exception e
                       {:exception e}))]
      (task-log/close-db-entry! db-id (t/now) res "finished")
      (finished! task-name db-name))))

(defn run-task-for-dbs!
  [task task-name task-id]
  (doseq [db-name (keys db/db-connections)]
    #_(log/debug "Running task " task-name "with id" task-id "for" db-name)
    (.execute task-pool (bound-fn*
                          #(run-db-task! db-name task task-name)))))