(ns bass4.responses.e-auth
  (:require [ring.util.http-response :as response]
            [schema.core :as s]
            [bass4.config :refer [env]]
            [bass4.services.bankid :as bankid]
            [bass4.layout :as layout]
            [bass4.utils :refer [kebab-case-keyword]]
            [bass4.http-utils :as h-utils]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [ring.util.http-response :as http-response]))

(def bankid-message-map
  {:pending {:outstanding-transaction {:auto   :rfa13
                                       :manual :rfa1
                                       :else   :rfa1}
             :no-client               {:auto   :rfa1
                                       :manual :rfa1
                                       :else   :rfa1}
             :started                 {:without-start-token :rfa14
                                       :with-start-token    :rfa15
                                       :else                :rfa14}
             :user-sign               :rfa9
             :else                    :rfa21}
   :failed  {:expired-transaction :rfa8
             :certificate-err     :rfa16
             :user-cancel         :rfa6
             :cancelled           :rfa3
             :start-failed        :rfa17
             :else                :rfa22}
   :error   {400   {:already-in-progress :rfa3
                    :invalid-parameters  :exception
                    :else                :rfa22}
             401   :exception
             404   :exception
             408   :rfa5
             415   :exception
             500   :rfa5
             503   :rfa5
             :else :exception}})

;; TODO: 503 allows for retries before showing RFA5

(defn get-bankid-message
  [status code & specifics]
  (let [status-responses (get bankid-message-map status)
        response         (get status-responses code)]
    (cond
      (nil? response)
      (:else status-responses)

      (keyword? response)
      response

      (map? response)
      (let [sub-response (select-keys response specifics)]
        (if (seq sub-response)
          (first (vals sub-response))
          (:else response))))))

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

(defn completed-data
  [info]
  (let [data {:personnummer (get-in info [:completion-data :user :personal-number])
              :first-name   (get-in info [:completion-data :user :given-name])
              :last-name    (get-in info [:completion-data :user :surname])}]
    (when-not (every? data [:personnummer :first-name :last-name])
      (throw (ex-info "BankID completed data invalid" data)))
    data))

;; TODO: Remove kebab-case out of here and into bankid service
(defn bankid-collect
  [session]
  (let [uid              (get-in session [:e-auth :uid])
        info             (bankid/get-session-info uid)
        status           (:status info)
        redirect-success (get-in session [:e-auth :redirect-success])]
    (if (= :complete status)
      (->
        (http-response/found redirect-success)
        (assoc :session (merge session {:e-auth (completed-data info)})))
      (h-utils/json-response
        (cond
          (= :exception status)
          (throw (:exception info))

          (nil? uid)
          {:status    :error
           :hint-code "No uid in session"
           :message   :no-session}

          (nil? info)
          {:status    :error
           :hint-code "No session info for uid "
           :message   :no-session}

          (contains? #{:starting :started} status)
          {:status    :starting
           :hint-code :contacting-bankid
           :message   :contacting-bankid}

          (= :error status)
          (let [error-code  (kebab-case-keyword (:error-code info))
                details     (kebab-case-keyword (:details info))
                http-status (:http-status info)]
            (log/debug info)
            {:status     :error
             :error-code error-code
             :details    details
             :message    (get-bankid-message status http-status error-code)})

          (contains? #{:pending :failed} status)
          (let [hint-code (kebab-case-keyword (:hint-code info))]
            {:status    status
             :hint-code hint-code
             :message   (get-bankid-message status hint-code)})

          :else
          (throw (ex-info (str "Unknown BankID status " status) info)))))))

(defn bankid-success
  [session]
  (let [personnummer (get-in session [:e-auth :personnummer])
        first-name   (get-in session [:e-auth :first-name])
        last-name    (get-in session [:e-auth :last-name])]
    (log/debug session)
    (if-not (and personnummer first-name last-name)
      (layout/error-403-page (:user-id session) "No BankID info in session")
      (layout/text-response (:e-auth session)))))