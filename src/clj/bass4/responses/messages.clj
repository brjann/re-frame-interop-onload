(ns bass4.responses.messages
  (:require [bass4.services.messages :as messages-service]
            [clojure.string :as string]
            [ring.util.http-response :as http-response]
            [schema.core :as s]
            [bass4.layout :as layout]
            [bass4.api-coercion :as api :refer [def-api]]))

(def-api messages-page [render-map :- map? user :- map?]
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

(def-api save-message
  [user-id :- integer? text :- api/str+!]
  (messages-service/save-message! user-id text)
  (http-response/found "/user/messages"))

(def-api save-draft
  [user-id :- integer? text :- api/str+!]
  (messages-service/save-draft! user-id text)
  (http-response/ok "ok"))

(def-api message-read
  [user-id :- integer? message-id :- api/int!]
  (messages-service/mark-message-as-read! user-id message-id)
  (http-response/ok "ok"))