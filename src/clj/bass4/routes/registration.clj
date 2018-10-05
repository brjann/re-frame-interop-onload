(ns bass4.routes.registration
  (:require [bass4.layout :as layout]
            [bass4.services.registration :as reg-service]
            [bass4.responses.registration :as reg-response]
            [bass4.utils :refer [str->int]]
            [compojure.core :refer [defroutes context GET POST routes]]
            [ring.util.http-response :as http-response]
            [clojure.tools.logging :as log]
            [bass4.route-rules :as route-rules]
            [bass4.middleware.core :as middleware]))

(defn reg-params-mw
  [handler]
  (fn [request]
    (let [[_ project-id-str _] (re-matches #"/registration/([0-9]+)(.*)" (:uri request))]
      (if-let [project-id (str->int project-id-str)]
        (if-let [reg-params (reg-service/registration-params project-id)]
          (if (:allowed? reg-params)
            (handler (assoc-in request [:db :reg-params] reg-params))
            (layout/text-response "Registration not allowed"))
          (layout/error-404-page))
        (layout/error-404-page)))))

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
  [{{:keys [registration]} :session} _]
  (let [consent (:privacy-consent registration)]
    (every? #(contains? consent %) [:notice-id :time])))

(def route-rules
  [{:uri   "/registration/:project/captcha"
    :rules [[#'spam-check-done? "form" :ok]
            [#'use-bankid? "bankid" :ok]]}

   {:uri   "/registration/:project/bankid"
    :rules [[#'spam-check-done? "form" :ok]
            [#'use-bankid? :ok "captcha"]]}

   {:uri   "/registration/:project/privacy"
    :rules [[#'spam-check-done? :ok "captcha"]]}

   {:uri   "/registration/:project/form"
    :rules [[#'spam-check-done? :ok "captcha"]
            [#'privacy-consent? :ok "privacy"]]}

   {:uri   "/registration/:project/validate*"
    :rules [[#'spam-check-done? :ok "captcha"]
            [#'privacy-consent? :ok "privacy"]
            [#'all-fields-present? :ok "form"]
            [#'needs-validation? :ok "form"]]}])

(defn registration-routes-mw
  [handler]
  (route-rules/wrap-route-mw
    handler
    ["/registration/*"]
    (route-rules/wrap-rules route-rules)
    reg-params-mw
    #'middleware/wrap-csrf))

(defroutes registration-routes
  (context "/registration/:project-id" [project-id]
    (GET "/" []
      (http-response/found (str "/registration/" project-id "/info")))

    (GET "/info" []
      (reg-response/info-page project-id))

    (GET "/bankid" []
      (reg-response/bankid-page project-id))
    (POST "/bankid" [personnummer :as request]
      (reg-response/bankid-poster project-id personnummer request))
    (GET "/bankid-finished" [:as request]
      (reg-response/bankid-finished project-id (:session request)))

    (GET "/privacy" []
      (reg-response/privacy-page project-id))
    (POST "/privacy" [i-consent :as request]
      (reg-response/handle-privacy-consent project-id i-consent (:session request)))

    (GET "/captcha" [:as request]
      (reg-response/captcha project-id (:session request)))
    (POST "/captcha" [captcha :as request]
      (reg-response/validate-captcha project-id captcha (:session request)))

    (GET "/form" [:as request]
      (reg-response/registration-page project-id (:session request)))
    (POST "/form" [& fields :as request]
      (reg-response/handle-registration project-id fields (:session request)))

    (GET "/validate" [:as request]
      (reg-response/validation-page project-id (:session request)))
    (POST "/validate-email" [code-email :as request]
      (reg-response/validate-email project-id code-email (:session request)))
    (POST "/validate-sms" [code-sms :as request]
      (reg-response/validate-sms project-id code-sms (:session request)))

    (GET "/duplicate" [:as request]
      (reg-response/duplicate-page project-id))

    (GET "/credentials" [:as request]
      (reg-response/credentials-page project-id (:session request) request))

    (GET "/finished" [:as request]
      (reg-response/finished-router project-id (:session request) request))

    (GET "/cancel" [:as request]
      (reg-response/cancel-registration project-id (:session request)))))