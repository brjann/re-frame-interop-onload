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
            [bass4.api-coercion :as api]
            [bass4.email :as email]
            [clojure.string :as str]))


;; ---------------------
;;     SMS TEKNIK
;; ---------------------


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


;; ---------------------
;;  ACTUAL EMAIL SENDER
;; ---------------------

(defn send-sms*!
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
          (throw (ex-info "SMS service returned zero" {:res res}))
          true))
      (catch Exception e
        (throw (ex-info "SMS sending error" {:exception e}))))))

(def ^:dynamic *sms-reroute* nil)

(defmulti send-sms! (fn [& _]
                      (let [re-route (or *sms-reroute* (env :dev-reroute-sms) :default)]
                        (if (string? re-route)
                          (if (str/includes? re-route "@")
                            :redirect-email
                            :redirect-sms)
                          re-route))))


(defmethod send-sms! :redirect-sms
  [recipient message sender]
  (let [reroute-sms (or *sms-reroute* (env :dev-reroute-sms) :default)]
    (send-sms*! reroute-sms (str "SMS to: " recipient "\n" message) sender)))


(defmethod send-sms! :redirect-email
  [recipient message _]
  (let [reroute-email (or *sms-reroute* (env :dev-reroute-sms) :default)]
    (email/send-email*! reroute-email "SMS" (str "To: " recipient "\n" message) nil false)))

(defmethod send-sms! :void
  [& _]
  true)

(defmethod send-sms! :out
  [& more]
  (println (apply str (interpose "\n" (conj more "SMS")))))

(defmethod send-sms! :default
  [to message sender]
  (send-sms*! to message sender))

(defmethod external-messages/external-message-sender :sms
  [{:keys [to message sender]}]
  (send-sms! to message sender))

(defn sms-success!
  [db-connection]
  (when db-connection
    (let [midnight (-> (bass/local-midnight)
                       (b-time/to-unix))]
      (db/increase-sms-count!
        db-connection
        {:day midnight}))))

(defn queue-sms!
  [to message]
  (let [sender     (try
                     (bass-service/db-sms-sender)
                     (catch Exception _
                       "BASS4"))
        error-chan (external-messages/async-error-chan email/error-sender (db-config/db-name))
        c          (external-messages/queue-message-c! {:type       :sms
                                                        :to         to
                                                        :message    message
                                                        :sender     sender
                                                        :error-chan error-chan})]
    (if db/*db*
      (go
        (let [res (<! c)]
          (if (= :error (:result res))
            (throw (ex-info "SMS send failed" res))
            (sms-success! db/*db*))))
      (log/info "No DB selected for SMS count update."))))