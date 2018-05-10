(ns bass4.routes.e-auth
  (:require [compojure.core :refer [defroutes context GET POST routes]]
            [ring.util.http-response :as response]
            [bass4.responses.e-auth :as e-auth-response]
            [bass4.services.user :as user]
            [bass4.request-state :as request-state]
            [bass4.layout :as layout]))

(defroutes e-auth-routes
  (context "/e-auth" []
    (GET "/bankid-test" []
      (layout/render "bankid-test.html"))
    (POST "/bankid" [& params :as request]
      (e-auth-response/launch-bankid (:session request) (:personnummer params) (:redirect params)))
    (GET "/bankid" [:as request]
      (e-auth-response/bankid-status-page (:session request)))
    (GET "/bankid-collect" [:as request]
      (e-auth-response/bankid-collect (:session request)))))