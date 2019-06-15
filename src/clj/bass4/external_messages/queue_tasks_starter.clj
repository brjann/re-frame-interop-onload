(ns bass4.external-messages.queue-tasks-starter
  (:require [mount.core :refer [defstate]]
            [bass4.task.scheduler :as task-scheduler]
            [bass4.external-messages.queue-tasks :as queue-tasks]))

(defstate email-task*
  :start (task-scheduler/schedule! #'queue-tasks/email-task ::task-scheduler/by-minute 5))

(defstate sms-task*
  :start (task-scheduler/schedule! #'queue-tasks/sms-task ::task-scheduler/by-minute 5))