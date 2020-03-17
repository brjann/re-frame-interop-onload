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
  [to message sender config]
  (when (config/env :dev)
    (log/info (str "Sent twilio sms to " to)))
  (let [{:keys [account-sid auth-token]} config
        url (url account-sid)]
    (try
      (let [res (-> (http/post url
                               {:basic-auth  [account-sid auth-token]
                                :form-params {"To"   to
                                              "Body" message
                                              "From" "+12015080358"}})
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