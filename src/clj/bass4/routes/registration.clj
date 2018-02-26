(ns bass4.routes.registration
  (:require [bass4.layout :as layout]
            [bass4.services.registration :as reg-service]
            [bass4.responses.registration :as reg-response]
            [bass4.utils :refer [str->int]]
            [compojure.core :refer [defroutes context GET POST routes]]
            [ring.util.http-response :as response]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

(defn captcha-mw
  [handler request]
  (let [[_ project-id path] (re-matches #"/registration/([0-9]*)(.*)", (:uri request))]
    (if (and project-id (reg-service/registration-allowed? (str->int project-id)))
      (if (or
            (= "/captcha" path)
            (= "/validate" path)
            (get-in request [:session :captcha-ok]))
        (handler request)
        (response/found (str "/registration/" project-id "/captcha")))
      (layout/text-response "Registration not allowed"))

    #_(if (string/starts-with? path "/registration/register/")
        (handler request))
    #_(if (get-in request [:session :captcha-ok])
        (handler request)
        (layout/text-response "CAPTCHA!"))))

(defroutes registration-routes
  (GET "/registration/:project-id" [project-id :as request]
    (reg-response/registration-page project-id))
  (POST "/registration/:project-id" [project-id & fields]
    (reg-response/handle-registration project-id fields))
  (GET "/registration/:project-id/captcha" [project-id :as request]
    (reg-response/captcha project-id (:session request)))
  (POST "/registration/:project-id/captcha" [project-id & params :as request]
    (reg-response/validate-captcha project-id (:captcha params) (:session request)))
  (GET "/registration/:project-id/validate" [project-id :as request]
    (reg-response/validation-page project-id (:session request)))
  (POST "/registration/:project-id/validate" [project-id & params :as request]
    (reg-response/handle-validation project-id params (:session request))))