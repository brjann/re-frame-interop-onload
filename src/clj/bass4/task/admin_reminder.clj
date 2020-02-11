(ns bass4.task.admin-reminder
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.set :as set]
            [mount.core :refer [defstate]]
            [bass4.utils :as utils]
            [bass4.db.core :as db]
            [bass4.services.bass :as bass-service]
            [bass4.external-messages.email-queue :as email-queue]
            [bass4.task.scheduler :as task-scheduler]
            [bass4.external-messages.email-sender :as email]))

(def reminder-names
  {::password-flags  "Lost password"
   ::open-flags      "Open flags"
   ::unread-homework "Unread homework reports"
   ::unread-messages "Unread message"})

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
  (db/admin-reminder-get-therapists db {:participant-ids participant-ids}))

(defn db-projects
  [db participant-ids]
  (db/admin-reminder-get-projects db {:participant-ids participant-ids}))

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

(defn projects-for-participants
  [db participant-ids therapists]
  (let [participant-projects (->> (map :participant-id therapists)
                                  (into #{})
                                  (set/difference participant-ids)
                                  (db-projects db)
                                  (group-by :project-id)
                                  (utils/map-map (fn [l] (map :participant-id l))))]
    (->> (keys participant-projects)
         (map (juxt #(vector 0 (:email (bass-service/db-contact-info* db %))) identity))
         (merge-project-emails)
         (utils/map-map (fn [l] (->> (mapcat #(get participant-projects %) l)
                                     (into #{})))))))

(defn collapse-therapists
  [therapists-for-participants]
  (->> therapists-for-participants
       (group-by (juxt :therapist-id :email))
       (utils/map-map (fn [l] (->> (map :participant-id l)
                                   (into #{}))))))

(defn insert-reminders
  [reminders-by-participants emails]
  (utils/map-map
    (fn [participant-ids]
      (->> participant-ids
           (map (juxt identity #(get reminders-by-participants %)))
           (into {})))
    emails))

(defn write-email
  [tz all-reminders]
  (apply str
         (map (fn [[user-id reminders]]
                (str "Participant internal ID: "
                     user-id "\n"
                     (apply str
                            (map (fn [{:keys [count type time]}]
                                   (str (get reminder-names type)
                                        " dated "
                                        (-> (f/formatter "yyyy-MM-dd HH:mm" tz)
                                            (f/unparse time))
                                        (str " count: " count)
                                        "\n"))
                                 reminders))
                     "\n"))
              all-reminders)))

(defn add-signature
  [local-config text]
  (str text "\nGreetings " (:name local-config)))

(defn prepend-project-notice
  [text]
  (str "The following participants do not have an assigned therapist. "
       "You are receiving this reminder because your email address is registered "
       "as project or database contact.\n\n"
       text))

(defn prepend-therapist-notice
  [text]
  (str "You are receiving this reminder because you are the registered therapist "
       "for the following participants.\n\n"
       text))

(defn remind-admins
  [db local-config now]
  (let [reminders-by-participants (collect-reminders db (t/minus now (t/days 60)))]
    (if (seq reminders-by-participants)
      (let [tz                          (-> (:timezone local-config)
                                            (t/time-zone-for-id))
            participant-ids             (into #{} (keys reminders-by-participants))
            therapists-for-participants (db-therapists db participant-ids)
            therapist-emails            (->> (collapse-therapists therapists-for-participants)
                                             (insert-reminders reminders-by-participants)
                                             (utils/map-map #(->> %
                                                                  (write-email tz)
                                                                  (add-signature local-config)
                                                                  (prepend-therapist-notice))))
            project-emails              (->> (projects-for-participants db participant-ids therapists-for-participants)
                                             (insert-reminders reminders-by-participants)
                                             (utils/map-map #(->> %
                                                                  (write-email tz)
                                                                  (add-signature local-config)
                                                                  (prepend-project-notice))))
            emails                      (->> (concat therapist-emails project-emails)
                                             (map (fn [[[user-id email] text]]
                                                    {:user-id user-id
                                                     :to      email
                                                     :subject "Unhandled participant tasks"
                                                     :message text}))
                                             (filter #(email/is-email? (:to %))))]
        (email-queue/add! db now emails)
        {:cycles (count emails)})
      {:cycles 0})))

(defstate remind-admins-task-starter
  :start (task-scheduler/schedule-db-task! #'remind-admins ::task-scheduler/daily-at 4))