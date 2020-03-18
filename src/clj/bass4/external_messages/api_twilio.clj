(ns bass4.external-messages.api-twilio
  (:require [ring.util.codec :as codec]
            [selmer.parser :as parser]
            [bass4.config :as config]
            [clojure.tools.logging :as log]
            [bass4.utils :as utils]
            [clj-http.client :as http]
            [clojure.data.json :as json]
            [clojure.pprint :as pprint]))



;; ---------------------
;;        TWILIO
;; ---------------------


(defn url
  [account-sid]
  (str "https://api.twilio.com/2010-04-01/Accounts/"
       account-sid
       "/Messages.json"))

(defn send!
  [to message _ config]
  (when (config/env :dev)
    (log/info (str "Sent sms using Twilio to " to)))
  (let [{:keys [account-sid auth-token from]} config
        url (url account-sid)]
    (try
      (let [form-params (merge {"To"   to
                                "Body" (str message
                                            "\n\nYou cannot reply to this message.")
                                "From" from}
                               (when (:status-url config)
                                 {"StatusCallback" (str (:status-url config) "/twilio")}))
            res         (-> (http/post url
                                       {:basic-auth  [account-sid auth-token]
                                        :form-params form-params})
                            :body
                            (json/read-str))]
        (if (get res "error_code")
          (throw (ex-info (str "Twilio returned error code " (get res "error_code")) res))
          (get res "sid")))
      (catch Exception e
        (let [data (ex-data e)]
          (if (:status data)
            (throw (ex-info (str "Twilio returned error status " (:status data)) data))
            (throw (ex-info "Error posting to Twilio" {:exception e}))))))))