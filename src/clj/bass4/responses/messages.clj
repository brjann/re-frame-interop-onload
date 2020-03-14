(ns bass4.responses.messages
  (:require [bass4.services.messages :as messages-service]
            [clojure.string :as str]
            [ring.util.http-response :as http-response]
            [schema.core :as schema]
            [bass4.layout :as layout]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.i18n :as i18n])
  (:import (org.joda.time DateTime)))

(defapi messages-page [render-map :- map? user :- map?]
  (let [user-id  (:user-id user)
        messages (->> (messages-service/get-all-messages user-id)
                      (map #(assoc % :text (str/escape (:text %) {\< "&lt;", \> "&gt;", \& "&amp;"}))))
        draft    (messages-service/get-draft user-id)]
    (layout/render "messages.html"
                   (merge render-map
                          {:user       user
                           :page-title (i18n/tr [:messages/messages])
                           :messages   messages
                           :draft      draft}))))

(defapi save-message
  [user-id :- integer? text :- [[api/str? 1 50000]]]
  (messages-service/save-message! user-id text)
  (http-response/found "messages"))

(defapi save-draft
  [user-id :- integer? text :- [[api/str? 0 50000]]]
  (messages-service/save-draft! user-id text)
  (http-response/ok "ok"))

(defapi message-read
  [user-id :- integer? message-id :- api/->int]
  (messages-service/mark-message-as-read! user-id message-id)
  (http-response/ok "ok"))

(defapi api-message-read
  [user-id :- integer? message-id :- api/->int]
  (messages-service/mark-message-as-read! user-id message-id)
  (http-response/ok {:result "ok"}))

(defapi api-save-message
  [user-id :- integer? text :- [[api/str? 1 50000]]]
  (messages-service/save-message! user-id text)
  (http-response/ok {:result "ok"}))

(schema/defschema Message
  {:message-id    schema/Int
   :unread?       (schema/maybe Boolean)
   :message       String
   :sender-name   String
   :send-datetime DateTime
   :sender-type   String})

(defapi api-messages
  [user :- map?]
  (let [user-id  (:user-id user)
        messages (messages-service/get-all-messages user-id)]
    (->> messages
         (mapv #(if (= user-id (:sender-id %))
                  (assoc % :unread? nil)
                  %))
         (mapv #(assoc % :message (:text %)))
         (mapv #(dissoc % :sender-class :sender-id :subject :text))
         (http-response/ok))))