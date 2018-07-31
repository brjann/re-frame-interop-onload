(ns bass4.responses.messages
  (:require [bass4.services.messages :as messages-service]
            [clojure.string :as string]
            [ring.util.http-response :as http-response]
            [schema.core :as s]
            [bass4.layout :as layout]))

(defn messages-page [render-map user]
  (let [user-id  (:user-id user)
        messages (->> (messages-service/get-all-messages user-id)
                      (map #(assoc % :text (string/escape (:text %) {\< "&lt;", \> "&gt;", \& "&amp;"}))))
        draft    (messages-service/get-draft user-id)]
    (layout/render "messages.html"
                   (merge render-map
                          {:user       user
                           :title      "Messages"
                           :page-title "Messages"
                           :messages   messages
                           :draft      draft}))))

(s/defn ^:always-validate save-message [user-id :- s/Int text :- s/Str]
  (messages-service/save-message! user-id text)
  (http-response/found "/user/messages"))

(s/defn ^:always-validate save-draft [user-id :- s/Int text :- s/Str]
  (messages-service/save-draft! user-id text)
  (http-response/ok "ok"))

(s/defn ^:always-validate message-read [user-id :- s/Int message-id :- s/Int]
  (messages-service/mark-message-as-read! user-id message-id)
  (http-response/ok "ok"))