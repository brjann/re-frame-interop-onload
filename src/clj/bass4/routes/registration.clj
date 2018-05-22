(ns bass4.routes.registration
  (:require [bass4.layout :as layout]
            [bass4.services.registration :as reg-service]
            [bass4.responses.registration :as reg-response]
            [bass4.utils :refer [str->int]]
            [compojure.core :refer [defroutes context GET POST routes]]
            [ring.util.http-response :as response]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

(defn registration-params
  [project-id-str]
  (let [project-id (str->int project-id-str)]
    (when project-id
      (let [params (reg-service/registration-params project-id)]
        (when (:allowed? params)
          params)))))

(defn captcha-bankid-mw
  [handler request]
  (let [[_ project-id path] (re-matches #"/registration/([0-9]+)(.*)" (:uri request))]
    (if-let [params (registration-params project-id)]
      (let [session     (:session request)
            captcha-ok? (get-in session [:registration :captcha-ok?])
            res         (case path
                          ("" "/")
                          (if (:bankid? params)
                            (if (reg-response/bankid-done? session)
                          true
                          "/bankid")
                            (if captcha-ok?
                          true
                          "/captcha"))

                          "/bankid"
                          (if (:bankid? params)
                            (if captcha-ok?
                          (if (reg-response/bankid-done? session)
                            "/"
                            true)
                          "/captcha")
                            "/")

                          "/captcha"
                          (if captcha-ok?
                        "/"
                        true)

                          true)]
        (cond
          (true? res)
          (handler request)

          (string? res)
          (response/found (str "/registration/" project-id res))

          :else
          (throw (ex-info "Checker returned illegal value" {:res res}))))
      (layout/text-response "Registration not allowed"))))

(defroutes registration-routes
  (GET "/registration/:project-id" [project-id :as request]
    (reg-response/registration-page project-id (:session request)))
  (POST "/registration/:project-id" [project-id & fields :as request]
    (reg-response/handle-registration project-id fields (:session request)))
  (GET "/registration/:project-id/" [project-id :as request]
    (reg-response/registration-page project-id (:session request)))
  (POST "/registration/:project-id/" [project-id & fields :as request]
    (reg-response/handle-registration project-id fields (:session request)))

  (GET "/registration/:project-id/bankid" [project-id :as request]
    (reg-response/bankid-page project-id))
  (POST "/registration/:project-id/bankid" [project-id & params :as request]
    (reg-response/bankid-poster project-id (:personnummer params) (:session request)))
  (GET "/registration/:project-id/bankid-finished" [project-id :as request]
    (reg-response/bankid-finished project-id (:session request)))

  (GET "/registration/:project-id/captcha" [project-id :as request]
    (reg-response/captcha project-id (:session request)))
  (POST "/registration/:project-id/captcha" [project-id & params :as request]
    (reg-response/validate-captcha project-id (:captcha params) (:session request)))

  (GET "/registration/:project-id/validate" [project-id :as request]
    (reg-response/validation-page project-id (:session request)))
  (POST "/registration/:project-id/validate-email" [project-id & params :as request]
    (reg-response/validate-code project-id :code-email (:code-email params) (:session request)))
  (POST "/registration/:project-id/validate-sms" [project-id & params :as request]
    (reg-response/validate-code project-id :code-sms (:code-sms params) (:session request)))

  (GET "/registration/:project-id/duplicate" [project-id :as request]
    (reg-response/duplicate-page project-id))

  (GET "/registration/:project-id/credentials" [project-id :as request]
    (reg-response/credentials-page project-id (:session request) request))

  (GET "/registration/:project-id/finished" [project-id :as request]
    (reg-response/finished-router project-id (:session request) request))

  (GET "/registration/:project-id/cancel" [project-id :as request]
    (reg-response/cancel-registration project-id (:session request))))