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
    (h-utils/json-response {"hejsan" "hooppsanXXX"})
    (layout/error-page {:status 500 :message "No active bankid session"})))