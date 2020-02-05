(ns bass4.external-messages.sms-sender
  (:require
    [clojure.core.async :refer [go <! chan put!]]
    [bass4.config :refer [env]]
    [clj-http.client :as http]
    [ring.util.codec :as codec]
    [bass4.db.core :as db]
    [bass4.services.bass :as bass-service]
    [selmer.parser :as parser]
    [bass4.db-common :as db-common]
    [clojure.tools.logging :as log]
    [bass4.services.bass :as bass]
    [bass4.external-messages.async :as external-messages]
    [bass4.external-messages.email-sender :as email]
    [clojure.string :as str]
    [bass4.utils :as utils]
    [bass4.config :as config]
    [bass4.external-messages.sms-status :as sms-status]
    [bass4.client-config :as client-config])
  (:import (clojure.core.async.impl.channels ManyToManyChannel)))


;; ---------------------
;;     SMS TEKNIK
;; ---------------------


;; Supporting UTF-8 charset
;; Set operationstype = 5 in XML
;; Characters that do not need escaping https://stackoverflow.com/questions/15866068/regex-to-match-gsm-character-set
;; Seems to work: #"^[\w@?£!1$\"¥#è?¤é%ù&ì\\ò(Ç)*:Ø+;ÄäøÆ,<LÖlöæ\-=ÑñÅß.>ÜüåÉ/§à¡¿\']+$"
;; SMS-teknik recommends the following regex
;; https://frightanic.com/software-development/regex-for-gsm-03-38-7bit-character-set/

(defn smsteknik-url
  [id user password]
  (str "https://api.smsteknik.se/send/xml/"
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
;;   ACTUAL SMS SENDER
;; ---------------------

; SMS-teknik return values
; 0:Access denied                 		No customer of the service or wrong id, username or password.
; 0:Parse error, [cause]			      	Parse error or no valid XML-data.
; 0:The required XML-tag <udmessage> is missing	The required xml-tag <udmessage> is missing.
; 0:Message could’t be empty			The message could’nt be empty
; 0:No SMS Left				      	The account are out of SMS.
; 0:Invalid phonenumber			        The number is not correct (eg. <8 or >16 numbers incl. leading +).
; 0:Invalid phonenumber (e164)			The number range is not defined in E164.
; 0:Number is blocked			        The number is blocked by SMS Teknik AB
; [SMSID] 					        SMS is a unique ID for every message that’s been queued. Use SMSID for
; remove scheduled message and matching delivery status.
;
; (The system will leave separate SMSID for every recipient in the request
;     and format the response with semicolon as delimiter)

(defn send-sms*!
  ([to message sender] (send-sms*! to message sender ""))
  ([to message sender status-url]
   (when (env :dev)
     (log/info (str "Sent sms to " to)))
   (let [config db-common/common-config
         url    (smsteknik-url
                  (:smsteknik-id config)
                  (:smsteknik-user config)
                  (:smsteknik-password config))
         xml    (smsteknik-xml
                  to
                  message
                  sender
                  status-url)]
     (try
       (let [res     (:body (http/post url {:body xml}))
             res-int (utils/str->int (subs res 0 1))]
         (if (zero? res-int)
           (throw (ex-info "SMS service returned zero" {:res res}))
           (utils/str->int res)))
       (catch Exception e
         (throw (ex-info "SMS sending error" {:exception e})))))))


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
  [recipient message sender status-url]
  (let [reroute-sms (or *sms-reroute* (env :dev-reroute-sms))]
    (send-sms*! reroute-sms (str "SMS to: " recipient "\n" message) sender status-url)))

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
  [to message sender status-url]
  (send-sms*! to message sender status-url))

;; -------------------------------------
;;       ASYNC SEND METHOD FOR SMS
;; -------------------------------------

(defmethod external-messages/async-message-sender :sms
  [{:keys [to message sender status-url]}]
  (send-sms! to message sender status-url))


;; ----------------------
;;        SMS API
;; ----------------------

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
  (let [sender     (get-sender db)
        status-url (sms-status/status-url db)
        res        (send-sms! to message sender status-url)]
    (assert (integer? res))
    (when res
      (if db
        (bass/inc-sms-count! db)
        (log/info "No DB selected for SMS count update.")))
    res))

(defn async-sms!
  "Throws if to is not valid mobile phone number.
  Returns channel on which send result will be put.
  Update of external message count is done before putting result."
  [db to message]
  (when-not (is-sms-number? to)
    (throw (throw (Exception. (str "Not valid sms number: " to)))))
  (let [sender     (get-sender db)
        status-url (sms-status/status-url db)
        error-chan (external-messages/async-error-chan email/error-sender (client-config/client-name))
        own-chan   (external-messages/queue-message! {:type       :sms
                                                      :to         to
                                                      :message    message
                                                      :sender     sender
                                                      :status-url status-url
                                                      :error-chan error-chan})]
    (go
      (let [res (<! own-chan)]
        (when-not (= :error (:result res))
          (if db
            (bass/inc-sms-count! db)
            (log/info "No DB selected for SMS count update.")))
        ;; Res is result of go block
        res))))