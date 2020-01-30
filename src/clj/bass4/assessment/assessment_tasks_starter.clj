(ns bass4.assessment.assessment-tasks-starter
  (:require [mount.core :refer [defstate]]
            [bass4.task.scheduler :as task-scheduler]
            [bass4.assessment.reminder :as assessment-reminder]
            [bass4.assessment.late-flagger :as late-flagger]
            [bass4.assessment.activated-flagger :as activated-flagger]))

(defstate assessments-tasks-starter
  :start (do
           (task-scheduler/schedule! #'assessment-reminder/reminder-task
                                     ::task-scheduler/hourly)
           (task-scheduler/schedule! #'late-flagger/late-flag-task
                                     ::task-scheduler/by-minute 1)
           (task-scheduler/schedule! #'late-flagger/late-deflag-task
                                     ::task-scheduler/by-minute 1)
           (task-scheduler/schedule! #'activated-flagger/activated-flag-task
                                     ::task-scheduler/by-minute 1)))