(ns bass4.task.admin-reminder
  (:require [bass4.utils :as utils]
            [bass4.db.core :as db]
            [clj-time.core :as t]
            [clojure.set :as set]
            [bass4.services.bass :as bass-service]
            [clojure.tools.logging :as log]))

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

(defn db-therapists
  [db participant-ids]
  (->> (db/admin-reminder-get-therapists db {:participant-ids participant-ids})))

(defn db-projects
  [db participant-ids]
  (->> (db/admin-reminder-get-projects db {:participant-ids participant-ids})))

(defn collect-reminders
  [db time-limit]
  (let [passwords  (db-lost-password-flags db time-limit)
        open-flags (db-open-flags db time-limit)
        messages   (db-unread-messages db time-limit)
        homework   (db-unread-homework db time-limit)]
    (->> [passwords open-flags messages homework]
         (map (fn [m] (group-by :user-id m)))
         (apply merge-with into))))

(defn merge-project-emails
  [project-emails]
  (reduce (fn [acc [k v]]
            (if (contains? acc k)
              (assoc acc k (conj (get acc k) v))
              (assoc acc k [v])))
          {}
          project-emails))

(defn email-recipients
  [db reminders-by-participants]
  (let [participant-ids      (into #{} (keys reminders-by-participants))
        therapists           (db-therapists db participant-ids)
        participant-projects (->> (map :participant-id therapists)
                                  (into #{})
                                  (set/difference participant-ids)
                                  (db-projects db)
                                  (group-by :project-id)
                                  (utils/map-map (fn [l] (map :participant-id l))))
        project-emails       (->> (keys participant-projects)
                                  (map (juxt #(:email (bass-service/db-contact-info* db %)) identity))
                                  (merge-project-emails)
                                  (utils/map-map (fn [l] (->> (mapcat #(get participant-projects %) l)
                                                              (into #{})))))
        therapist-emails     (->> therapists
                                  (group-by :email)
                                  (utils/map-map (fn [l] (->> (map :participant-id l)
                                                              (into #{})))))]
    [therapist-emails
     project-emails]))

(defn remind-admins
  [db _ now]
  (let [reminders-by-participants (collect-reminders db (t/minus now (t/days 60)))
        therapist+projects        (email-recipients db reminders-by-participants)]
    (map (fn [x] (utils/map-map (fn [participant-ids]
                                  (map #(get reminders-by-participants %) participant-ids))
                                x))
         therapist+projects)))