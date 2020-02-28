(ns bass4.services.messages
  (:require [bass4.db.core :as db]
            [bass4.utils :refer [map-map]]
            [clj-time.core :as t]
            [bass4.db.orm-classes :as orm]))

(defn create-message-placeholder
  [user-id]
  (let [message-id
        ((keyword "objectid")
         (orm/create-bass-object-map! {:class-name    "cMessage"
                                       :parent-id     user-id
                                       :property-name "Messages"}))]
    (db/set-message-sender! {:message-id message-id :user-id user-id})
    message-id))

(defn- get-draft-id [user-id]
  (or (:message-id (db/get-message-draft {:user-id user-id}))
      (create-message-placeholder user-id)))

(defn save-message! [user-id text]
  (let [message-id (get-draft-id user-id)]
    (db/save-message! {:message-id message-id :subject "" :text text})
    message-id))

(defn save-draft! [user-id text]
  (let [message-id (get-draft-id user-id)]
    (db/save-message-draft! {:message-id message-id :subject "" :text text})
    message-id))

(defn get-draft [user-id]
  (db/get-message-draft {:user-id user-id}))

(defn get-all-messages [user-id]
  (db/get-all-messages {:user-id user-id}))

(defn mark-message-as-read! [user-id message-id]
  (when (< 0 (db/mark-message-as-read! {:user-id user-id :message-id message-id}))
    (db/set-message-reader! {:user-id user-id :message-id message-id})
    true))

(defn new-messages? [user-id]
  (->> (db/new-messages {:user-id user-id})
       :new-messages-count
       (not= 0)))