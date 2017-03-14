(ns bass4.responses.messages
  (:require [bass4.services.messages :as messages-service]
            [bass4.views.messages :as messages-view]
            [bass4.services.user :as user]
            [ring.util.http-response :as response]))

(defn messages-page [{user-id :identity} errors]
  (let [user (user/get-user user-id)
        messages (messages-service/get-all-messages user-id)
        draft (messages-service/get-draft user-id)]
    (messages-view/messages-page user messages draft errors)))

(defn save-message [{:keys [subject text]} {user-id :identity}]
  (messages-service/save-message! user-id subject text)
  (response/ok {:result :ok}))

(defn save-draft [{:keys [subject text]} {user-id :identity}]
  (messages-service/save-draft! user-id subject text)
  (response/ok {:result :ok}))