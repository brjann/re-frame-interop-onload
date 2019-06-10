(ns bass4.external-messages.sms-sender
  (:require
    [clojure.core.async :refer [go <! chan]]
    [bass4.config :refer [env]]
    [clj-http.client :as http]
    [ring.util.codec :as codec]
    [bass4.db.core :as db]
    [bass4.services.bass :as bass-service]
    [selmer.parser :as parser]
    [bass4.db-config :as db-config]
    [clojure.tools.logging :as log]
    [bass4.services.bass :as bass]
    [bass4.time :as b-time]
    [bass4.external-messages.async :as external-messages]
    [bass4.external-messages.email-sender :as email]
    [clojure.string :as str]
    [bass4.utils :as utils]))


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
;;   ACTUAL SMS SENDER
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
            res-int (utils/str->int (subs res 0 1))]
        (if (zero? res-int)
          (throw (ex-info "SMS service returned zero" {:res res}))
          true))
      (catch Exception e
        (throw (ex-info "SMS sending error" {:exception e}))))))


;; ----------------------
;;  SMS SENDER REROUTING
;; ----------------------

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
  [& _])

(defmethod send-sms! :out
  [& more]
  (println (apply str (interpose "\n" (conj more "SMS")))))

(defmethod send-sms! :default
  [to message sender]
  (send-sms*! to message sender))

;; -------------------------------------
;;    EXTERNAL MESSAGE METHOD FOR SMS
;; -------------------------------------

(defmethod external-messages/external-message-sender :sms
  [{:keys [to message sender]}]
  (send-sms! to message sender))


;; ----------------------
;;        SMS API
;; ----------------------

(defn is-sms-number?
  [number]
  (re-matches #"^\+{0,1}[0-9()./\- ]+$" number))

(defn get-sender []
  (if db/*db*
    (bass-service/db-sms-sender)
    "BASS4"))

(defn send-sms-now!
  [to message]
  (when-not (is-sms-number? to)
    (throw (throw (Exception. (str "Not valid sms number: " to)))))
  (let [sender (get-sender)]
    (send-sms! to message sender)
    (if db/*db*
      (bass/inc-sms-count! db/*db*)
      (log/info "No DB selected for SMS count update."))
    true))

(defn queue-sms!
  "Throws if to is not valid mobile phone number.
  Returns channel on which send result will be put.
  Guarantees that update of external message count is done before putting result"
  [to message]
  (when-not (is-sms-number? to)
    (throw (throw (Exception. (str "Not valid sms number: " to)))))
  (let [sender     (get-sender)
        error-chan (external-messages/async-error-chan email/error-sender (db-config/db-name))
        own-chan   (external-messages/queue-message! {:type       :sms
                                                      :to         to
                                                      :message    message
                                                      :sender     sender
                                                      :error-chan error-chan})]
    (go
      (let [res (<! own-chan)]
        (when-not (= :error (:result res))
          (if db/*db*
            (bass/inc-sms-count! db/*db*)
            (log/info "No DB selected for SMS count update.")))
        ;; Res is result of go block
        res))))