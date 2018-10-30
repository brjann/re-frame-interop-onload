(ns bass4.sms-sender
  (:require [clojure.java.io :as io]
            [clojure.core.async :refer [go <! chan]]
            [bass4.config :refer [env]]
            [bass4.email :refer [send-email!]]
            [clj-http.client :as http]
            [ring.util.codec :as codec]
            [bass4.db.core :as db]
            [bass4.services.bass :as bass-service]
            [selmer.parser :as parser]
            [bass4.request-state :as request-state]
            [bass4.db-config :as db-config]
            [clojure.tools.logging :as log]
            [bass4.services.bass :as bass]
            [bass4.time :as b-time]
            [bass4.external-messages :as external-messages]
            [bass4.api-coercion :as api]))

(defn smsteknik-url
  [id user password]
  (str "https://www.smsteknik.se/Member/SMSConnectDirect/SendSMSv3.asp"
       "?id=" (codec/url-encode id)
       "&user=" (codec/url-encode user)
       "&pass=" (codec/url-encode password)))

(defn smsteknik-xml
  [recipient message sender return-url]
  (parser/render-file
    "smsteknik.xml"
    {:sender                  sender
     :delivery-status-address return-url
     :message                 message
     :recipient               recipient}))

(defn ^String sms-error
  [recipient message res]
  (let [error (str "Could not send sms."
                   "\nRecipient " recipient
                   "\nMessage: " message
                   "\nError: " res)]
    (request-state/record-error! error)
    error))



(defn send-sms*!
  [recipient message]
  (when (env :dev)
    (log/info (str "Sent sms to " recipient)))
  (let [config db-config/common-config
        url    (smsteknik-url
                 (:smsteknik-id config)
                 (:smsteknik-user config)
                 (:smsteknik-password config))
        xml    (smsteknik-xml
                 recipient
                 message
                 (bass-service/db-sms-sender)
                 (:smsteknik-status-return-url config))
        res    (:body (http/post url {:body xml}))]
    (if (= "0" (subs res 0 1))
      (throw (Exception. (sms-error recipient message res)))
      true)))

(defn send-sms!
  [to message sender]
  (when (env :dev)
    (log/info (str "Sent sms to " to)))
  (let [config db-config/common-config
        url    (smsteknik-url
                 (:smsteknik-id config)
                 (:smsteknik-user config)
                 (:smsteknik-password config))
        xml    (smsteknik-xml
                 to
                 message
                 sender
                 (:smsteknik-status-return-url config))]
    (try
      (let [res     (:body (http/post url {:body xml}))
            res-int (api/int! res)]
        (if (zero? res-int)
          (throw (Exception. "SMS service returned zero"))
          true))
      (catch Exception e
        (throw (ex-info "SMS sending error" {:exception e}))))))

(defn sms-success!
  [db-connection]
  (when db-connection
    (let [midnight (-> (bass/local-midnight)
                       (b-time/to-unix))]
      (db/increase-sms-count!
        db-connection
        {:day midnight}))))

;; Overwritten by other function when in debug mode
(defn ^:dynamic send-db-sms!
  [recipient message]
  (let [db-connection db/*db*]
    (try
      (when (send-sms*! recipient message)
        (sms-success! db-connection)
        true)
      (catch Exception e false))))


(defmethod external-messages/send-external-message :sms
  [{:keys [to message sender]}]
  (send-sms! to message sender))

(defn queue-sms!
  [to message]
  (let [sender (bass-service/db-sms-sender)
        c      (external-messages/queue-message-c! {:type    :sms
                                                    :to      to
                                                    :message message
                                                    :sender  sender})]
    (go
      (let [res (<! c)]
        (if (= :error (:result res))
          (throw (ex-info "SMS send failed" res))
          (do
            (sms-success! db/*db*)
            (log/debug "Increasing SMS-count")))))))