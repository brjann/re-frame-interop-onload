(ns bass4.responses.e-auth
  (:require [ring.util.http-response :as response]
            [schema.core :as s]
            [bass4.config :refer [env]]
            [bass4.services.bankid :as bankid]
            [bass4.layout :as layout]
            [bass4.utils :refer [kebab-case-keyword]]
            [bass4.http-utils :as h-utils]
            [clojure.tools.logging :as log]
            [ring.util.http-response :as http-response]
            [bass4.i18n :as i18n]
            [clojure.string :as string]))



;; --------------------------------
;;              NOTE
;; --------------------------------
;;
;;  POST requests are wrapped in CSRF. So if the app is restarted, then test
;;  screen will report "you have been logged out".
;;


;; --------------------------------
;;            LAUNCHER
;; --------------------------------

(s/defn
  ^:always-validate
  launch-bankid
  [session personnummer :- s/Str redirect-success :- s/Str redirect-fail :- s/Str]
  (if (re-matches #"[0-9]{12}" personnummer)
    (let [uid (bankid/launch-bankid personnummer)]
      (-> (response/found "/e-auth/bankid/status")
          (assoc :session (merge
                            session
                            {:e-auth {:uid              uid
                                      :type             :bankid
                                      :redirect-success redirect-success
                                      :redirect-fail    redirect-fail}}))))
    (layout/error-422 "error")))

;; --------------------------------
;;           STATUS PAGE
;; --------------------------------

(defn bankid-status-page
  [session]
  (let [uid              (get-in session [:e-auth :uid])
        bankid?          (= :bankid (get-in session [:e-auth :type]))
        redirect-success (get-in session [:e-auth :redirect-success])
        redirect-fail    (get-in session [:e-auth :redirect-fail])]
    (if (and uid bankid? redirect-success redirect-fail)
      (layout/render "bankid-status.html")
      (layout/error-400-page "No active BankID session"))))


;; --------------------------------
;;   COLLECTING DATA FROM BANKID
;; --------------------------------

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
             :cancelled           :rfa3-first
             :start-failed        :rfa17
             :else                :rfa22}
   :error   {400   {:already-in-progress :rfa3-second
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

(defn completed-data
  [info]
  (let [data {:personnummer (get-in info [:completion-data :user :personal-number])
              :first-name   (get-in info [:completion-data :user :given-name])
              :last-name    (get-in info [:completion-data :user :surname])}]
    (when-not (every? data [:personnummer :first-name :last-name])
      (throw (ex-info "BankID completed data invalid" data)))
    data))

;; TODO: Remove kebab-case out of here and into bankid service
(defn bankid-collect-response
  [uid status info]
  (cond
    (= :exception status)
    (throw (:exception info))

    (nil? uid)
    {:title     (i18n/tr [:bankid/error])
     :status    :error
     :hint-code "No uid in session"
     :message   :no-session}

    (nil? info)
    {:title     (i18n/tr [:bankid/error])
     :status    :error
     :hint-code (str "No session info for uid " uid)
     :message   :no-session}

    (contains? #{:starting :started} status)
    {:title     (i18n/tr [:bankid/contacting])
     :status    :starting
     :hint-code :contacting-bankid
     :message   :contacting-bankid}

    (= :error status)
    (let [error-code  (kebab-case-keyword (:error-code info))
          details     (kebab-case-keyword (:details info))
          http-status (:http-status info)]
      {:title      (i18n/tr [:bankid/error])
       :status     :error
       :error-code error-code
       :details    details
       :message    (get-bankid-message status http-status error-code)})

    (contains? #{:pending :failed} status)
    (let [hint-code (kebab-case-keyword (:hint-code info))]
      {:title     (i18n/tr (if (= :pending status) [:bankid/pending] [:bankid/failed]))
       :status    status
       :hint-code hint-code
       :message   (get-bankid-message status hint-code)})

    :else
    (throw (ex-info (str "Unknown BankID status " status) info))))

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
      (let [response (bankid-collect-response uid status info)
            message  (:message response)]
        (if (= :exception message)
          (throw (ex-info "BankID error" response))
          (h-utils/json-response
            (merge
              response
              {:message (i18n/tr [(keyword (str "bankid/" (name message)))])})))))))

(defn bankid-success
  [session]
  (let [personnummer (get-in session [:e-auth :personnummer])
        first-name   (get-in session [:e-auth :first-name])
        last-name    (get-in session [:e-auth :last-name])]
    (if (and personnummer first-name last-name)
      (layout/text-response (:e-auth session))
      (layout/error-400-page "No BankID info in session"))))

;; --------------------------------
;;   CANCELLING AND ABORTING REQS
;; --------------------------------
(defn bankid-reset
  "Resets the e-auth map in session and redirects to redirect-failure
  This is the response to the user clicking Cancel in the status page"
  [session]
  (let [uid           (get-in session [:e-auth :uid])
        bankid?       (= :bankid (get-in session [:e-auth :type]))
        redirect-fail (get-in session [:e-auth :redirect-fail])]
    (let [response (when (and uid bankid?)
                     (bankid/cancel-bankid! uid)
                     (when redirect-fail
                       (-> (response/found redirect-fail)
                           (assoc :session (dissoc session :e-auth)))))]
      (if response
        response
        (layout/error-400-page "No active BankID session")))))

(defn bankid-cancel
  "Cancels a bankid request and resets e-auth map in session.
  This is the response to the user leaving the status page
  (if the authentication is still pending)"
  [session]
  (let [uid     (get-in session [:e-auth :uid])
        bankid? (= :bankid (get-in session [:e-auth :type]))]
    (when (and uid bankid?)
      (bankid/cancel-bankid! uid)
      (-> (response/ok)
          (assoc :session (dissoc session :e-auth))))))

;; This is not thought through enough. Not implemented.
#_(defn bankid-middleware
  [handler request]
  (let [e-auth (get-in request [:session :e-auth])]
    #_(handler request)
    (if (and
          (:uid e-auth)
          (= :bankid (:type e-auth))
          (not (string/starts-with? (:uri request) "/e-auth/bankid")))
      (http-response/found "/e-auth/bankid/status")
      (handler request))))