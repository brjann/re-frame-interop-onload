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
  [session personnummer :- s/Str redirect-success :- s/Str redirect-fail :- s/Str]
  (if (re-matches #"[0-9]{12}" personnummer)
    (let [uid (bankid/launch-bankid personnummer)]
      (-> (response/found "/e-auth/bankid")
          (assoc :session (merge
                            session
                            {:e-auth {:uid              uid
                                      :type             :bankid
                                      :redirect-success redirect-success
                                      :redirect-fail    redirect-fail}}))))
    (layout/error-422 "error")))

(defn bankid-status-page
  [session]
  (let [uid              (get-in session [:e-auth :uid])
        bankid?          (= :bankid (get-in session [:e-auth :type]))
        redirect-success (get-in session [:e-auth :redirect-success])
        redirect-fail    (get-in session [:e-auth :redirect-fail])]
    (if (and uid bankid? redirect-success redirect-fail)
      (layout/render "bankid-status.html")
      (layout/error-403-page (:user-id session) "No active BankID session"))))

(defn bankid-collect
  [session]
  (if-let [uid (get-in session [:e-auth :uid])]
    (let [info (bankid/get-session-info uid)]
      (if (nil? info)
        (h-utils/json-response {:status    :error
                                :hint-code (str "No session info for uid " uid)})
        (h-utils/json-response {:status       (:status info)
                                :hint-code    (:hint-code info)
                                :personnummer (get-in info [:completion-data :user :personal-number])
                                :name         (get-in info [:completion-data :user :name])})))
    (h-utils/json-response {:status    :error
                            :hint-code "No uid in session"})))

(defn bankid-success
  [session]
  (let [personnummer (get-in session [:e-auth :personnummer])
        first-name   (get-in session [:e-auth :first-name])
        last-name    (get-in session [:e-auth :last-name])]
    (if-not (and personnummer first-name last-name)
      (layout/error-403-page (:user-id session) "No BankID info in session")
      (layout/text-response "OK"))))