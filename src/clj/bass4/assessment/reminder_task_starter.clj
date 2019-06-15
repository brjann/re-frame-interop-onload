(ns bass4.assessment.reminder-task-starter
  (:require [mount.core :refer [defstate]]
            [bass4.task.scheduler :as task-scheduler]
            [bass4.assessment.reminder :as assessment-reminder]))

(defstate reminder-task-starter
  :start (task-scheduler/schedule! #'assessment-reminder/reminder-task ::task-scheduler/hourly))