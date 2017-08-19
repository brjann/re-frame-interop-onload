(ns bass4.responses.messages
  (:require [bass4.services.messages :as messages-service]
            [bass4.services.user :as user]
            [ring.util.http-response :as response]
            [schema.core :as s]
            [bass4.layout :as layout]))

(defn- messages-page [user messages draft]
  (layout/render
    "messages.html"
    {:user user
     :title "Messages"
     :active_messages true
     :messages messages
     :draft draft}))

(defn messages-page [render-fn user]
  (let [user-id (:user-id user)
        messages (messages-service/get-all-messages user-id)
        draft (messages-service/get-draft user-id)]
    (render-fn "messages.html" {:user            user
                                :title           "Messages"
                                :active_messages true
                                :messages        messages
                                :draft           draft})))

(s/defn ^:always-validate save-message [user-id :- s/Int subject :- s/Str text :- s/Str]
  (messages-service/save-message! user-id subject text)
  (response/found "/user/messages"))

(s/defn ^:always-validate save-draft [user-id :- s/Int subject :- s/Str text :- s/Str]
  (messages-service/save-draft! user-id subject text)
  (response/ok "ok"))