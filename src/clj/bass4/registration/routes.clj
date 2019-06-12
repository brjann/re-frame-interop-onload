(ns bass4.registration.routes
  (:require [bass4.layout :as layout]
            [bass4.registration.services :as reg-service]
            [bass4.registration.responses :as reg-response]
            [bass4.utils :refer [str->int]]
            [compojure.core :refer [defroutes context GET POST routes]]
            [ring.util.http-response :as http-response]
            [clojure.tools.logging :as log]
            [bass4.route-rules :as route-rules]
            [bass4.middleware.core :as middleware]
            [bass4.services.privacy :as privacy-service]))

(defn reg-params-mw
  [handler]
  (fn [request]
    (let [[_ project-id-str _] (re-matches #"/registration/([0-9]+)(.*)" (:uri request))]
      (if-let [project-id (str->int project-id-str)]
        (if-let [reg-params (reg-service/registration-params project-id)]
          (if (:allowed? reg-params)
            (handler (assoc-in request [:db :reg-params]
                               (merge reg-params
                                      {:privacy-notice-disabled? (privacy-service/privacy-notice-disabled?)})))
            (layout/text-response "Registration not allowed"))
          (http-response/not-found))
        (http-response/not-found)))))

(defn logged-in?
  [{:keys [session]} _]
  (boolean (:user-id session)))

(defn session?
  [{:keys [session]} _]
  (not (empty? session)))

(defn spam-check-done?
  [{{:keys [registration]} :session {:keys [reg-params]} :db} _]
  (let [captcha-ok?  (:captcha-ok? registration)
        bankid?      (:bankid? reg-params)
        bankid-done? (:bankid-done? registration)]
    (or captcha-ok? (and bankid? bankid-done?))))

(defn use-bankid?
  [{{:keys [reg-params]} :db} _]
  (:bankid? reg-params))

(defn needs-validation?
  [{{:keys [registration]} :session} _]
  (let [codes (:validation-codes registration)]
    (or (contains? codes :code-sms) (contains? codes :code-email))))

(defn all-fields-present?
  [{{:keys [registration]} :session {:keys [reg-params]} :db} _]
  (let [field-values (:field-values registration)]
    (reg-response/all-fields? (:fields reg-params) field-values)))

(defn privacy-consent?
  [{{:keys [registration]} :session {:keys [reg-params]} :db} _]
  (or (:privacy-notice-disabled? reg-params)
      (let [consent (:privacy-consent registration)]
        (every? #(contains? consent %) [:notice-id :time]))))

(defn study-consent-required?
  [{{:keys [registration]} :session {:keys [reg-params]} :db} _]
  (and (:study-consent? reg-params)
       (not (let [consent (:study-consent registration)]
              (every? #(contains? consent %) [:consent-id :time])))))

(defn privacy-disabled?
  [{{:keys [reg-params]} :db} _]
  (:privacy-notice-disabled? reg-params))

(def route-rules
  [{:uri   "/registration/:project/info"
    :rules [[#'logged-in? "logged-in" :ok]
            [#'session? "clear-session" :ok]]}

   {:uri   "/registration/:project/captcha"
    :rules [[#'logged-in? "logged-in" :ok]
            [#'spam-check-done? "form" :ok]
            [#'use-bankid? "bankid" :ok]]}

   {:uri   "/registration/:project/bankid"
    :rules [[#'logged-in? "logged-in" :ok]
            [#'spam-check-done? "form" :ok]
            [#'use-bankid? :ok "captcha"]]}

   {:uri   "/registration/:project/privacy"
    :rules [[#'spam-check-done? :ok "captcha"]
            [#'privacy-disabled? "form" :ok]]}

   {:uri   "/registration/:project/study-consent"
    :rules [[#'spam-check-done? :ok "captcha"]
            [#'privacy-consent? :ok "privacy"]
            [#'study-consent-required? :ok "form"]]}

   {:uri   "/registration/:project/form"
    :rules [[#'spam-check-done? :ok "captcha"]
            [#'privacy-consent? :ok "privacy"]
            [#'study-consent-required? "study-consent" :ok]]}

   {:uri   "/registration/:project/validate*"
    :rules [[#'spam-check-done? :ok "captcha"]
            [#'privacy-consent? :ok "privacy"]
            [#'study-consent-required? "study-consent" :ok]
            [#'all-fields-present? :ok "form"]
            [#'needs-validation? :ok "form"]]}
   {:uri   "/registration/:project/resume-assessment"
    :rules [[#'logged-in? :ok 403]]}
   {:uri   "/registration/:project/no-credentials-resume-info"
    :rules [[#'logged-in? :ok 403]]}])

(defn registration-routes-mw
  [handler]
  (route-rules/wrap-route-mw
    handler
    ["/registration/*"]
    (route-rules/wrap-rules route-rules)
    reg-params-mw
    #'middleware/wrap-csrf))

(defroutes registration-routes
  (context "/registration/:project-id" [project-id :as
                                        {:keys                [session]
                                         {:keys [reg-params]} :db}]
    (GET "/" []
      (http-response/found (str "/registration/" project-id "/info")))

    (GET "/logged-in" []
      (reg-response/logged-in-page))

    (GET "/clear-session" []
      (-> (http-response/found "info")
          (assoc :session nil)))

    (GET "/info" []
      (reg-response/info-page project-id))

    (GET "/captcha" []
      (reg-response/captcha project-id session))
    (POST "/captcha" [captcha]
      (reg-response/validate-captcha project-id captcha session))

    (GET "/bankid" []
      (reg-response/bankid-page project-id))
    (POST "/bankid" [personnummer :as request]
      (reg-response/bankid-poster project-id personnummer request))
    (GET "/bankid-finished" []
      (reg-response/bankid-finished project-id session reg-params))

    (GET "/privacy" []
      (reg-response/privacy-page project-id))
    (POST "/privacy" [i-consent]
      (reg-response/handle-privacy-consent project-id i-consent session))

    (GET "/study-consent" []
      (reg-response/study-consent-page project-id))
    (POST "/study-consent" [i-consent]
      (reg-response/handle-study-consent project-id i-consent session))

    (GET "/form" []
      (reg-response/registration-page project-id session))
    (POST "/form" [& fields]
      (reg-response/handle-registration project-id fields session reg-params))

    (GET "/validate" []
      (reg-response/validation-page project-id session))
    (POST "/validate-email" [code-email]
      (reg-response/validate-email project-id code-email session reg-params))
    (POST "/validate-sms" [code-sms]
      (reg-response/validate-sms project-id code-sms session reg-params))

    (GET "/duplicate" []
      (reg-response/duplicate-page project-id))

    (GET "/credentials" [:as request]
      (reg-response/credentials-page project-id session request))

    (GET "/finished" [:as request]
      (reg-response/finished-router project-id session reg-params))

    (GET "/no-credentials-resume-info" [:as request]
      (reg-response/no-credentials-resume-info-page project-id))

    (GET "/resuming-finished" []
      (reg-response/resuming-finished-page project-id))
    (GET "/resuming-assessments" [:as request]
      (reg-response/resuming-assessments-page project-id request))

    (GET "/cancel" []
      (reg-response/cancel-registration project-id session))))