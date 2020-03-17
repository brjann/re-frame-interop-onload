(ns bass4.external-messages.api-sms-teknik
  (:require [ring.util.codec :as codec]
            [selmer.parser :as parser]
            [bass4.config :as config]
            [clojure.tools.logging :as log]
            [bass4.utils :as utils]
            [clj-http.client :as http]))



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

(defn send!
  [to message sender config]
  (when (config/env :dev)
    (log/info (str "Sent sms to " to)))
  (let [url (smsteknik-url
              (:smsteknik-id config)
              (:smsteknik-user config)
              (:smsteknik-password config))
        xml (smsteknik-xml
              to
              message
              sender
              (str (:status-url config) "/sms-teknik"))]
    (try
      (let [res     (:body (http/post url {:body xml}))
            res-int (utils/str->int (subs res 0 1))]
        (if (zero? res-int)
          (throw (ex-info "SMS service returned zero" {:res res}))
          (utils/str->int res)))
      (catch Exception e
        (throw (ex-info "SMS sending error" {:exception e}))))))