(ns bass4.sms-sender
  (:require [clojure.java.io :as io]
            [clojure.core.async :refer [go <! chan]]
            [bass4.config :refer [env]]
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

(defn sms-success!
  [db-connection]
  (when db-connection
    (let [midnight (-> (bass/local-midnight)
                       (b-time/to-unix))]
      (db/increase-sms-count!
        db-connection
        {:day midnight}))))


(defn send-sms*!
  [to message sender]
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

;; Overwritten by other function when in debug mode
(defn ^:dynamic send-sms!
  [to message sender]
  (when (env :dev)
    (log/info (str "Sent sms to " to)))
  (send-sms*! to message sender))

(defmethod external-messages/external-message-sender :sms
  [{:keys [to message sender]}]
  ;; Bind the function to local var and close over it,
  ;; to respect dynamic bindings.
  (let [sms-sender send-sms!]
    ;; This function is executed in another thread
    (fn [] (sms-sender to message sender))))

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
          (sms-success! db/*db*))))))