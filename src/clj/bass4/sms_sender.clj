(ns bass4.sms-sender
  (:require [clojure.java.io :as io]
            [bass4.config :refer [env]]
            [bass4.mailer :refer [mail!]]
            [clj-http.client :as http]
            [ring.util.codec :as codec]
            [bass4.db.core :as db]
            [bass4.services.bass :as bass-service]
            [selmer.parser :as parser]
            [bass4.request-state :as request-state]
            [bass4.bass-locals :as locals]
            [clojure.tools.logging :as log]
            [bass4.services.bass :as bass]
            [bass4.time :as b-time]))

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

;; TODO: Increase db sms counter
(defn sms-success!
  [db-connection]
  (let [midnight (-> (bass/local-midnight)
                     (b-time/to-unix))]
    (db/increase-sms-count!
      db-connection
      {:day midnight})))

(defn send-sms*!
  [recipient message]
  (when (env :dev)
    (log/info (str "Sent sms to " recipient)))
  (let [config locals/common-config
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

;; Overwritten by other function when in debug mode
(defn send-db-sms!
  [recipient message]
  (let [db-name       (:name locals/*local-config*)
        db-connection db/*db*]
    (try
      (when (send-sms*! recipient message)
        (sms-success! db-connection)
        true)
      (catch Exception e false))))