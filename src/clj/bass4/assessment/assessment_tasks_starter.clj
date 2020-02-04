(ns bass4.assessment.assessment-tasks-starter
  (:require [mount.core :refer [defstate]]
            [bass4.task.scheduler :as task-scheduler]
            [bass4.assessment.reminder :as assessment-reminder]
            [bass4.assessment.late-flagger :as late-flagger]
            [bass4.assessment.activated-flagger :as activated-flagger]))

(defstate assessments-tasks-starter
  :start (do
           (task-scheduler/schedule-db-task! #'assessment-reminder/reminder-task
                                             ::task-scheduler/hourly)
           (task-scheduler/schedule-db-task! #'late-flagger/late-flag-task
                                             ::task-scheduler/hourly)
           (task-scheduler/schedule-db-task! #'late-flagger/late-deflag-task
                                             ::task-scheduler/hourly)
           (task-scheduler/schedule-db-task! #'activated-flagger/activated-flag-task
                                             ::task-scheduler/hourly)))