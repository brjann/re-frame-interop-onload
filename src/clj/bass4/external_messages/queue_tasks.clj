(ns bass4.external-messages.queue-tasks
  (:require [mount.core :refer [defstate]]
            [bass4.task.scheduler :as task-scheduler]
            [bass4.external-messages.email-queue :as email-queue]
            [bass4.external-messages.sms-queue :as sms-queue]))

(defn task-res
  [res]
  (assoc res :cycles (+ (:fail res) (:success res))))

(defn email-task
  [db-name now]
  (-> (email-queue/send! db-name now)
      (task-res)))

(defn sms-task
  [db-name now]
  (-> (sms-queue/send! db-name now)
      (task-res)))

(defstate email-task*
  :start (task-scheduler/schedule! #'email-task ::task-scheduler/by-minute 5))

(defstate sms-task*
  :start (task-scheduler/schedule! #'sms-task ::task-scheduler/by-minute 5))