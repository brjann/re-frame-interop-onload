(ns bass4.task.admin-reminder
  (:require [bass4.utils :as utils]
            [bass4.db.core :as db]))

(defn db-lost-password-flags
  [db time-limit]
  (db/admin-reminder-lost-password-flags db {:time-limit (utils/to-unix time-limit)}))

(defn db-open-flags
  [db time-limit]
  (db/admin-reminder-open-flags db {:time-limit (utils/to-unix time-limit)}))

(defn db-unread-messages
  [db time-limit]
  (db/admin-reminder-unread-messages db {:time-limit (utils/to-unix time-limit)}))

(defn db-unread-homework
  [db time-limit]
  (db/admin-reminder-unread-homework db {:time-limit (utils/to-unix time-limit)}))

(defn collect-reminders
  [])