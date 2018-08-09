(ns bass4.routes.registration
  (:require [bass4.layout :as layout]
            [bass4.services.registration :as reg-service]
            [bass4.responses.registration :as reg-response]
            [bass4.utils :refer [str->int]]
            [compojure.core :refer [defroutes context GET POST routes]]
            [ring.util.http-response :as http-response]
            [clojure.tools.logging :as log]
            [buddy.auth.accessrules :as buddy-rules]))

(defn registration-params
  [request]
  (let [[_ project-id-str _] (re-matches #"/registration/([0-9]+)(.*)" (:uri request))]
    (let [project-id (str->int project-id-str)]
      (when project-id
        (let [reg-params (reg-service/registration-params project-id)]
          (when (:allowed? reg-params)
            [project-id reg-params]))))))

(defn- eval-rules
  [request & rules]
  (let [[project-id reg-params] (registration-params request)]
    (if project-id
      (let [reg-session (get-in request [:session :registration])
            res         (loop [rules rules]
                          (if (empty? rules)
                            true
                            (let [[pred pred-true pred-false] (first rules)
                                  res (if (pred reg-params reg-session)
                                        pred-true
                                        pred-false)]
                              (if (= :ok res)
                                (recur (rest rules))
                                res))))]
        (cond
          (true? res)
          true

          (string? res)
          (if (= :get (:request-method request))
            (buddy-rules/error (http-response/found (str "/registration/" project-id res)))
            (buddy-rules/error (layout/error-400-page)))

          :else
          (throw (Exception. "Rule did not return true or string"))))
      (buddy-rules/error (layout/text-response "Registration not allowed")))))

(defn spam-check-done?
  [reg-params reg-session]
  (let [captcha-ok?  (:captcha-ok? reg-session)
        bankid?      (:bankid? reg-params)
        bankid-done? (:bankid-done? reg-session)]
    (or captcha-ok? (and bankid? bankid-done?))))

(defn use-bankid?
  [reg-params _]
  (:bankid? reg-params))

(defn needs-validation?
  [_ reg-session]
  (let [codes (:validation-codes reg-session)]
    (or (contains? codes :code-sms) (contains? codes :code-email))))

(defn all-fields-present?
  [reg-params reg-session]
  (let [field-values (:field-values reg-session)]
    (reg-response/all-fields? (:fields reg-params) field-values)))

(defn privacy-consent?
  [_ reg-session]
  (let [consent (:privacy-consent reg-session)]
    (every? #(contains? consent %) [:privacy-notice :time])))

(def route-rules
  [{:pattern #"^/registration/[0-9]+/info"
    :handler (fn [request] (eval-rules request))}

   {:pattern #"^/registration/[0-9]+/captcha"
    :handler (fn [request] (eval-rules request
                                       [spam-check-done? "/form" :ok]
                                       [use-bankid? "/bankid" :ok]))}

   {:pattern #"^/registration/[0-9]+/bankid"
    :handler (fn [request] (eval-rules request
                                       [spam-check-done? "/form" :ok]
                                       [use-bankid? :ok "/captcha"]))}

   {:pattern #"^/registration/[0-9]+/privacy"
    :handler (fn [request] (eval-rules request
                                       [spam-check-done? :ok "/captcha"]))}

   {:pattern #"^/registration/[0-9]+/form"
    :handler (fn [request] (eval-rules request
                                       [spam-check-done? :ok "/captcha"]
                                       [privacy-consent? :ok "/privacy"]))}

   {:pattern #"^/registration/[0-9]+/validate.*"
    :handler (fn [request] (eval-rules request
                                       [spam-check-done? :ok "/captcha"]
                                       [privacy-consent? :ok "/privacy"]
                                       [all-fields-present? :ok "/form"]
                                       [needs-validation? :ok "/form"]))}])


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

#_(defroutes registration-routes
    (GET "/registration/:project-id" []
      (layout/text-response "You are not supposed to be here."))
    (GET "/registration/:project-id/" []
      (layout/text-response "You are not supposed to be here."))

    (GET "/registration/:project-id/info" [project-id]
      (reg-response/info-page project-id))

    (GET "/registration/:project-id/bankid" [project-id]
      (reg-response/bankid-page project-id))
    (POST "/registration/:project-id/bankid" [project-id personnummer :as request]
      (reg-response/bankid-poster project-id personnummer request))
    (GET "/registration/:project-id/bankid-finished" [project-id :as request]
      (reg-response/bankid-finished project-id (:session request)))

    (GET "/registration/:project-id/captcha" [project-id :as request]
      (reg-response/captcha project-id (:session request)))
    (POST "/registration/:project-id/captcha" [project-id captcha :as request]
      (reg-response/validate-captcha project-id captcha (:session request)))

    (GET "/registration/:project-id/form" [project-id :as request]
      (reg-response/registration-page project-id (:session request)))
    (POST "/registration/:project-id/form" [project-id & fields :as request]
      (reg-response/handle-registration project-id fields (:session request)))

    (GET "/registration/:project-id/validate" [project-id :as request]
      (reg-response/validation-page project-id (:session request)))
    (POST "/registration/:project-id/validate-email" [project-id code-email :as request]
      (reg-response/validate-email project-id code-email (:session request)))
    (POST "/registration/:project-id/validate-sms" [project-id code-sms :as request]
      (reg-response/validate-sms project-id code-sms (:session request)))

    (GET "/registration/:project-id/duplicate" [project-id :as request]
      (reg-response/duplicate-page project-id))

    (GET "/registration/:project-id/credentials" [project-id :as request]
      (reg-response/credentials-page project-id (:session request) request))

    (GET "/registration/:project-id/finished" [project-id :as request]
      (reg-response/finished-router project-id (:session request) request))

    (GET "/registration/:project-id/cancel" [project-id :as request]
      (reg-response/cancel-registration project-id (:session request))))