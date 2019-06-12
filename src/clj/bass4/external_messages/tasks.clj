(ns bass4.external-messages.tasks
  (:require [mount.core :refer [defstate]]
            [bass4.task.scheduler :as task-scheduler]
            [bass4.external-messages.email-queue :as email-queue]
            [bass4.external-messages.sms-queue :as sms-queue]))

(defstate email-task
  :start (task-scheduler/schedule! #'email-queue/send! ::task-scheduler/by-minute 5))

(defstate sms-task
  :start (task-scheduler/schedule! #'sms-queue/send! ::task-scheduler/by-minute 5))