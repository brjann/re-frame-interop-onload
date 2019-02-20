(ns bass4.responses.messages
  (:require [bass4.services.messages :as messages-service]
            [clojure.string :as string]
            [ring.util.http-response :as http-response]
            [schema.core :as s]
            [bass4.layout :as layout]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.i18n :as i18n]
            [clojure.tools.logging :as log]
            [bass4.http-utils :as h-utils]))

(defapi messages-page [render-map :- map? user :- map?]
  (let [user-id  (:user-id user)
        messages (->> (messages-service/get-all-messages user-id)
                      (map #(assoc % :text (string/escape (:text %) {\< "&lt;", \> "&gt;", \& "&amp;"}))))
        draft    (messages-service/get-draft user-id)]
    (layout/render "messages.html"
                   (merge render-map
                          {:user       user
                           :page-title (i18n/tr [:messages/messages])
                           :messages   messages
                           :draft      draft}))))

(defapi save-message
  [user-id :- integer? text :- [[api/str? 1 5000]]]
  (messages-service/save-message! user-id text)
  (http-response/found "messages"))

(defapi save-draft
  [user-id :- integer? text :- [[api/str? 0 5000]]]
  (messages-service/save-draft! user-id text)
  (http-response/ok "ok"))

(defapi message-read
  [user-id :- integer? message-id :- api/->int]
  (messages-service/mark-message-as-read! user-id message-id)
  (http-response/ok "ok"))


(defapi api-messages
  [user :- map?]
  (let [user-id  (:user-id user)
        messages (messages-service/get-all-messages user-id)]
    (->> messages
         (mapv #(if (= user-id (:sender-id %))
                  (assoc % :unread? nil)
                  %))
         (mapv #(dissoc % :sender-class :sender-id :subject))
         (http-response/ok))))