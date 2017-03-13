(ns bass4.services.messages
  (:require [bass4.layout :as layout]
            [bass4.db.core :as db]
            [ring.util.http-response :as response]))

(defn- create-message-placeholder [user-id]
  (let [message-id
        ((keyword "objectid")
          (db/create-bass-object! {:class-name "cMessage"
                                   :parent-id user-id
                                   :property-name "Messages"}))]
    (db/set-message-sender! {:message-id message-id :user-id user-id})
    message-id))

(defn- get-draft-id [user-id]
  (or (:message-id (db/get-message-draft {:user-id user-id}))
      (create-message-placeholder user-id)))

(defn- save-message! [user-id subject text]
  (let [message-id (get-draft-id user-id)]
    (db/save-message! {:message-id message-id :subject subject :text text})
    message-id))

(defn- save-draft! [user-id subject text]
  (let [message-id (get-draft-id user-id)]
    (db/save-message-draft! {:message-id message-id :subject subject :text text})
    message-id))

;; API functions
(defn new-message! [{:keys [subject text]} {user-id :identity}]
  (save-message! user-id subject text)
  (response/ok {:result :ok}))

(defn x-save-draft! [{:keys [subject text]} {user-id :identity}]
  (save-draft! user-id subject text)
  (response/ok {:result :ok}))

(defn messages-page [{:keys [identity]} errors]
  (let [user (db/get-user {:id identity})
        messages (db/get-all-messages {:user-id identity})]
    (layout/render
      "messages.html"
      {:user user
       :title "Messages"
       :active_messages true
       :messages messages
       :errors errors})))