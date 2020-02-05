(ns bass4.task.admin-reminder
  (:require [bass4.utils :as utils]
            [bass4.db.core :as db]))

(defn db-lost-password-flags
  [time-limit]
  (db/admin-reminder-lost-password-flags {:time-limit (utils/to-unix time-limit)}))

(defn db-open-flags
  [time-limit]
  (db/admin-reminder-open-flags {:time-limit (utils/to-unix time-limit)}))

(defn db-unread-messages
  [time-limit]
  (db/admin-reminder-unread-messages {:time-limit (utils/to-unix time-limit)}))

(defn db-unread-homework
  [time-limit]
  (db/admin-reminder-unread-homework {:time-limit (utils/to-unix time-limit)}))