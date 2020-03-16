(ns bass4.services.bass
  (:require [bass4.db.core :as db]
            [bass4.config :refer [env]]
            [bass4.utils :refer [map-map-keys str->int json-safe]]
            [bass4.utils :as utils]
            [bass4.clients.time :as client-time]))

(defn db-title []
  (:title (db/get-db-title)))

(defn db-sms-sender [db]
  (:sms-sender (db/get-sms-sender db {})))

(defn db-contact-info*
  ([db project-id]
   (let [emails (db/get-contact-info db {:project-id project-id})]
     (assoc emails :email (if-not (empty? (:project-email emails))
                            (:project-email emails)
                            (:db-email emails))))))

(defn db-contact-info
  ([] (db-contact-info 0))
  ([project-id]
   (db-contact-info* db/*db* project-id)))

(defn project-names
  [db]
  (->> (db/project-names db {})
       (map (juxt :project-id :name))
       (into {})))

(defn- inc-external-message-count!
  [db-connection type]
  (when db-connection
    (let [midnight (-> (client-time/local-midnight)
                       (utils/to-unix))]
      (db/inc-external-message-count!
        db-connection
        {:type type
         :day  midnight}))))

(defn inc-sms-count!
  [db-connection]
  (inc-external-message-count! db-connection "sms"))

(defn inc-email-count!
  [db-connection]
  (inc-external-message-count! db-connection "email"))