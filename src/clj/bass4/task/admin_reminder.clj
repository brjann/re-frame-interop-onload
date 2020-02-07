(ns bass4.task.admin-reminder
  (:require [bass4.utils :as utils]
            [bass4.db.core :as db]
            [clj-time.core :as t]))

(defn db-lost-password-flags
  [db time-limit]
  (->> (db/admin-reminder-lost-password-flags db {:time-limit (utils/to-unix time-limit)})
       (map #(assoc % :type ::password-flags))))

(defn db-open-flags
  [db time-limit]
  (->> (db/admin-reminder-open-flags db {:time-limit (utils/to-unix time-limit)})
       (map #(assoc % :type ::open-flags))))

(defn db-unread-messages
  [db time-limit]
  (->> (db/admin-reminder-unread-messages db {:time-limit (utils/to-unix time-limit)})
       (map #(assoc % :type ::unread-messages))))

(defn db-unread-homework
  [db time-limit]
  (->> (db/admin-reminder-unread-homework db {:time-limit (utils/to-unix time-limit)})
       (map #(assoc % :type ::unread-homework))))

(defn collect-reminders
  [db time-limit]
  (let [passwords  (db-lost-password-flags db time-limit)
        open-flags (db-open-flags db time-limit)
        messages   (db-unread-messages db time-limit)
        homework   (db-unread-homework db time-limit)
        user-ids   (->> [passwords open-flags messages homework]
                        (map (fn [m] (group-by :user-id m)))
                        (apply merge-with into))]
    user-ids))