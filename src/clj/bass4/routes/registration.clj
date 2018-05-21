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

(defn captcha-mw
  [handler request]
  (let [[_ project-id path] (re-matches #"/registration/([0-9]+)(.*)" (:uri request))]
    (if-let [params (registration-params project-id)]
      (let [session (:session request)
            res     (case path
                      ("" "/")
                      (if (:bankid? params)
                        (if (reg-response/bankid-done? session)
                          true
                          "/bankid")
                        (if (:captcha-ok session)
                          true
                          "/captcha"))

                      "/bankid"
                      (if (:bankid? params)
                        (if (:captcha-ok session)
                          (if (reg-response/bankid-done? session)
                            "/"
                            true)
                          "/captcha")
                        "/")

                      "/captcha"
                      (if (:captcha-ok session)
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

#_(defn captcha-mw
    [handler request]
    (let [[_ project-id path] (re-matches #"/registration/([0-9]+)(.*)", (:uri request))]
      (if (and project-id (reg-service/registration-allowed? (str->int project-id)))

        (if (or
              (= "/captcha" path)
              (= "/validate" path)
              (= "/duplicate" path)
              (= "/credentials" path)
              (= "/finished" path)
              (get-in request [:session :captcha-ok]))
          (handler request)
          (response/found (str "/registration/" project-id "/captcha")))
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
    (reg-response/bankid-page project-id (:session request)))
  (POST "/registration/:project-id/bankid" [project-id & params :as request]
    (reg-response/bankid-poster project-id (:personnummer params) (:session request)))

  (GET "/registration/:project-id/captcha" [project-id :as request]
    (reg-response/captcha project-id (:session request)))
  (POST "/registration/:project-id/captcha" [project-id & params :as request]
    (reg-response/validate-captcha project-id (:captcha params) (:session request)))

  (GET "/registration/:project-id/validate" [project-id :as request]
    (reg-response/validation-page project-id (:session request)))
  (POST "/registration/:project-id/validate" [project-id & params :as request]
    (reg-response/handle-validation project-id params (:session request)))

  (GET "/registration/:project-id/duplicate" [project-id :as request]
    (reg-response/duplicate-page project-id))

  (GET "/registration/:project-id/credentials" [project-id :as request]
    (reg-response/credentials-page project-id (:session request) request))

  (GET "/registration/:project-id/finished" [project-id :as request]
    (reg-response/finished-router project-id (:session request) request)))