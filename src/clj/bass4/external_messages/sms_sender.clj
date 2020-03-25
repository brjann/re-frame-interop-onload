(ns bass4.external-messages.sms-sender
  (:require
    [clojure.core.async :refer [go <! chan put!]]
    [bass4.config :refer [env]]
    [ring.util.codec :as codec]
    [bass4.services.bass :as bass-service]
    [selmer.parser :as parser]
    [bass4.db-common :as db-common]
    [clojure.tools.logging :as log]
    [bass4.services.bass :as bass]
    [bass4.external-messages.async :as external-messages]
    [bass4.external-messages.email-sender :as email]
    [clojure.string :as str]
    [bass4.config :as config]
    [bass4.external-messages.sms-status :as sms-status]
    [bass4.external-messages.api-sms-teknik :as sms-teknik]
    [bass4.external-messages.api-twilio :as twilio]
    [bass4.clients.core :as clients])
  (:import (clojure.core.async.impl.channels ManyToManyChannel)))


(defn send-sms*!
  [to message sender config]
  (case (:provider config)
    :sms-teknik
    (sms-teknik/send! to message sender config)

    :twilio
    (twilio/send! to message sender config)

    (throw (ex-info "Unknown provider" config))))


;; ----------------------
;;  SMS SENDER REROUTING
;; ----------------------

(def ^:dynamic *sms-reroute* nil)

(defmulti send-sms! (fn [& _]
                      (let [re-route (or *sms-reroute* (env :dev-reroute-sms) :default)]
                        (cond
                          (string? re-route)
                          (if (str/includes? re-route "@")
                            :redirect-email
                            :redirect-sms)

                          (instance? ManyToManyChannel re-route)
                          :chan

                          :else
                          re-route))))


(defmethod send-sms! :redirect-sms
  [recipient message sender config]
  (let [reroute-sms (or *sms-reroute* (env :dev-reroute-sms))]
    (send-sms*! reroute-sms (str "SMS to: " recipient "\n" message) sender config)))

(defmethod send-sms! :redirect-email
  [recipient message & _]
  (let [reroute-email (or *sms-reroute* (env :dev-reroute-sms))]
    (email/send-email*! reroute-email "SMS" (str "To: " recipient "\n" message) (config/env :no-reply-address) nil false)))

(defmethod send-sms! :void
  [& _]
  666)

(defmethod send-sms! :out
  [& more]
  (println (apply str (interpose "\n" (conj more "SMS"))))
  666)

(defmethod send-sms! :exception
  [& _]
  (throw (Exception. "An exception")))

(defmethod send-sms! :chan
  [& more]
  (let [c *sms-reroute*]
    (put! c more))
  666)

(defmethod send-sms! :default
  [to message sender config]
  (send-sms*! to message sender config))

;; -------------------------------------
;;       ASYNC SEND METHOD FOR SMS
;; -------------------------------------

(defmethod external-messages/async-message-sender :sms
  [{:keys [to message sender config]}]
  (send-sms! to message sender config))


;; ----------------------
;;        SMS API
;; ----------------------

(defn sms-config
  ([] (sms-config nil))
  ([db]
   (let [config (when db
                  (clients/client-setting* (clients/db->client-name db)
                                           [:sms-config]
                                           nil))]
     (assoc
       (if config
         config
         (let [config db-common/common-config]
           (assoc
             (select-keys config [:smsteknik-id :smsteknik-user :smsteknik-password])
             :provider :sms-teknik)))
       :status-url (when db (sms-status/status-url db))))))

(defn is-sms-number?
  [number]
  (re-matches #"^\+{0,1}[0-9()./\- ]+$" number))

(defn get-sender [db]
  (let [sender (when db (bass-service/db-sms-sender db))]
    (if-not (zero? (count sender))
      sender
      "BASS4")))

(defn send-sms-now!
  "Throws if to is not valid mobile phone number."
  [db to message]
  (when-not (is-sms-number? to)
    (throw (throw (Exception. (str "Not valid sms number: " to)))))
  (let [sender (get-sender db)
        sms-id (send-sms! to message sender (sms-config db))]
    (when sms-id
      (if db
        (bass/inc-sms-count! db)
        (log/info "No DB selected for SMS count update.")))
    sms-id))

(defn async-sms!
  "Throws if to is not valid mobile phone number.
  Returns channel on which send result will be put.
  Update of external message count is done before putting result."
  [db to message]
  (when-not (is-sms-number? to)
    (throw (throw (Exception. (str "Not valid sms number: " to)))))
  (let [sender     (get-sender db)
        error-chan (external-messages/async-error-chan email/error-sender (clients/client-setting [:name]))
        own-chan   (external-messages/queue-message! {:type       :sms
                                                      :to         to
                                                      :message    message
                                                      :sender     sender
                                                      :config     (sms-config db)
                                                      :error-chan error-chan})]
    (go
      (let [res (<! own-chan)]
        (when-not (= :error (:result res))
          (if db
            (bass/inc-sms-count! db)
            (log/info "No DB selected for SMS count update.")))
        ;; Res is result of go block
        res))))