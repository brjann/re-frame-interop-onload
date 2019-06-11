(ns bass4.task.adder
  (:require [bass4.task.scheduler :as scheduler]
            [clojure.tools.logging :as log]))


(defmacro add-hourly-task!
  [task]
  (let [task-name# (str *ns* "/" (if (symbol? task)
                                   (name task)
                                   (str task)))]
    `(scheduler/schedule! ~task ~task-name# :hourly)))

(defmacro add-by-minute-task!
  [task interval]
  (let [task-name# (str *ns* "/" (if (symbol? task)
                                   (name task)
                                   (str task)))]
    `(scheduler/schedule! ~task ~task-name# :by-minute ~interval)))

(defmacro add-by-millisecond-task!
  [task interval]
  (let [task-name# (str *ns* "/" (if (symbol? task)
                                   (name task)
                                   (str task)))]
    `(scheduler/schedule! ~task ~task-name# :by-millisecond ~interval)))

(defmacro cancel-task!
  [task]
  (let [task-name# (str *ns* "/" (if (symbol? task)
                                   (name task)
                                   (str task)))]
    `(scheduler/cancel-task*! ~task-name#)))

(defn task-tester
  [db-name now]
  (log/debug "Running task for" db-name)
  {:cycles 100})

(defn task-tester2
  [db-name now]
  (log/debug "Running task for" db-name)
  {:cycles 100})

(defn task-tester3
  [db-name now]
  (log/debug "Running task for" db-name)
  {:cycles 100})

(defn task-tester4
  [db-name now]
  (log/debug "Running task for" db-name)
  {:cycles 100})

(defn task-tester5
  [db-name now]
  (log/debug "Running task for" db-name)
  {:cycles 100})

(defn task-tester6
  [db-name now]
  (log/debug "Running task for" db-name)
  {:cycles 100})