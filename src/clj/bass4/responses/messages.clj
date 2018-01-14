(ns bass4.responses.messages
  (:require [bass4.services.messages :as messages-service]
            [bass4.services.user :as user]
            [clojure.string :as string]
            [ring.util.http-response :as response]
            [schema.core :as s]
            [bass4.layout :as layout]))

(defn messages-page [render-fn user]
  (let [user-id  (:user-id user)
        messages (->> (messages-service/get-all-messages user-id)
                      (map #(assoc % :text (string/escape (:text %) {\< "&lt;", \> "&gt;", \& "&amp;"}))))
        draft    (messages-service/get-draft user-id)]
    (render-fn "messages.html" {:user            user
                                :title           "Messages"
                                :page-title      "Messages"
                                :messages        messages
                                :draft           draft})))

(s/defn ^:always-validate save-message [user-id :- s/Int text :- s/Str]
  (messages-service/save-message! user-id text)
  (response/found "/user/messages"))

(s/defn ^:always-validate save-draft [user-id :- s/Int text :- s/Str]
  (messages-service/save-draft! user-id text)
  (response/ok "ok"))

(s/defn ^:always-validate message-read [user-id :- s/Int message-id :- s/Int]
  (messages-service/mark-message-as-read! user-id message-id)
  (response/ok "ok"))