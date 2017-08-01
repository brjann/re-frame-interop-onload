(ns bass4.sms-sender
  (:require [clojure.java.io :as io]
            [bass4.config :refer [env]]
            [clj-http.client :as http]
            [ring.util.codec :as codec]
            [bass4.db.core :as db]
            [bass4.services.bass :as bass-service]
            [selmer.parser :as parser]
            [bass4.request-state :as request-state])
  (:import (java.io StringReader)))

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

(defn sms-error
  [recipient message res]
  (request-state/record-error!
    (str "Could not send sms."
         "\nRecipient " recipient
         "\nMessage: " message
         "\nError: " res))
  false)


;; TODO: Increase db sms counter
(defn sms-success
  []
  true)

(defn send-db-sms
  [recipient message]
  (let [config (:common db/db-configs)
        url    (smsteknik-url
                 (:SMSTEKNIK_ID config)
                 (:SMSTEKNIK_USER config)
                 (:SMSTEKNIK_PASSWORD config))
        xml    (smsteknik-xml
                 recipient
                 message
                 (bass-service/db-sms-sender)
                 (:SMSTEKNIK_STATUS_RETURN_URL config))
        res    (:body (http/post url {:body xml}))]
    (if (= "0" (subs res 0 1))
      (sms-error recipient message res)
      (sms-success))))