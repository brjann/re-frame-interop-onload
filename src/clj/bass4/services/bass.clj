(ns bass4.services.bass
  (:require [bass4.db.core :as db]
            [clj-time.core :as t]
            [bass4.config :refer [env]]
            [clojure.tools.logging :as log]
            [bass4.utils :refer [map-map-keys str->int json-safe]]
            [bass4.utils :as utils]
            [bass4.clients.core :as clients]
            [bass4.clients.time :as client-time]))

(defn db-title []
  (:title (db/get-db-title)))

(defn db-sms-sender [db]
  (:sms-sender (db/get-sms-sender db {})))

(defn db-url
  [db]
  (:url (db/get-db-url db {})))

(defn db-contact-info*
  ([db project-id]
   (let [emails (db/get-contact-info {:project-id project-id})]
     (assoc emails :email (if-not (empty? (:project-email emails))
                            (:project-email emails)
                            (:db-email emails))))))

(defn db-contact-info
  ([] (db-contact-info 0))
  ([project-id]
   (db-contact-info* db/*db* project-id)))

(declare local-midnight)

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


;
;(defonce uids (atom (cache/ttl-cache-factory {} :ttl (* 1000 60 60 24))))
;
;(defn uid-for-data!
;  [data]
;  (let [uid (UUID/randomUUID)]
;    ))