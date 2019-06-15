(ns bass4.assessment.reminder-task
  (:require [mount.core :refer [defstate]]
            [clj-time.core :as t]
            [bass4.task.scheduler :as task-scheduler]
            [bass4.db.core :as db]
            [bass4.db-config :as db-config]
            [bass4.assessment.reminder :as assessment-reminder]
            [bass4.external-messages.email-queue :as email-queue]
            [bass4.external-messages.sms-queue :as sms-queue]
            [clojure.tools.logging :as log]))

(defn reminder-task
  [db-name now]
  (let [db               @(get db/db-connections db-name)
        tz               (-> (get-in db-config/local-configs [:db-name :time-zone] "Europe/Stockholm")
                             (t/time-zone-for-id))
        start+stop-hours (db/get-reminder-start-and-stop db)
        hour             (t/hour (t/to-time-zone now tz))]
    (if (and (>= hour (:start-hour start+stop-hours))
             (< hour (:stop-hour start+stop-hours)))
      (let [res (assessment-reminder/remind! db
                                             now
                                             tz
                                             #(email-queue/add! db now %)
                                             #(sms-queue/add! db now %))]
        {:cycles res})
      {})))

(defstate reminder-task*
  :start (task-scheduler/schedule! #'reminder-task ::task-scheduler/by-minute 1))