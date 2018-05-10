(ns bass4.responses.e-auth
  (:require [ring.util.http-response :as response]
            [schema.core :as s]
            [bass4.config :refer [env]]
            [bass4.services.bankid :as bankid]
            [bass4.layout :as layout]
            [bass4.http-utils :as h-utils]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [ring.util.http-response :as http-response]))
(s/defn
  ^:always-validate
  launch-bankid
  [session personnummer :- s/Str redirect :- s/Str]
  (let [uid (bankid/launch-bankid personnummer)]
    (-> (response/found "/e-auth/bankid")
        (assoc :session (merge session {:e-auth {:uid      uid
                                                 :type     :bankid
                                                 :redirect redirect}})))))

(defn bankid-status-page
  [session]
  (if-let [uid (get-in session [:e-auth :uid])]
    (layout/render "bankid-status.html")
    (layout/text-response "no active bankid session")))

(defn bankid-collect
  [session]
  (if-let [uid (get-in session [:e-auth :uid])]
    (let [info (bankid/get-session-info uid)]
      (if (nil? info)
        (h-utils/json-response {:error (str "No session info for uid " uid)})
        (h-utils/json-response {:status       (:status info)
                                :hint-code    (:hint-code info)
                                :personnummer (get-in info [:completion-data :user :personalNumber])
                                :name         (get-in info [:completion-data :user :name])})))
    (h-utils/json-response {:error "No uid in session"})))