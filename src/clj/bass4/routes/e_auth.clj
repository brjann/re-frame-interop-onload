(ns bass4.routes.e-auth
  (:require [compojure.core :refer [defroutes context GET POST routes]]
            [ring.util.http-response :as response]
            [bass4.responses.e-auth :as e-auth-response]
            [bass4.services.user :as user]
            [bass4.request-state :as request-state]
            [bass4.layout :as layout]))

(defroutes e-auth-routes
  (context "/e-auth" []
    (context "/bankid" []
      (GET "/test" []
        (layout/render "bankid-test.html"))
      (POST "/launch" [& params :as request]
        (e-auth-response/launch-bankid-test
          (:session request)
          (:personnummer params)
          (:redirect-success params)
          (:redirect-fail params)))
      (GET "/status" [:as request]
        (e-auth-response/bankid-status-page (:session request)))
      (GET "/cancel" [return-url :as request]
        (e-auth-response/bankid-cancel (:session request) return-url))
      (GET "/reset" [:as request]
        (e-auth-response/bankid-reset (:session request)))
      (GET "/success" [:as request]
        (e-auth-response/bankid-success (:session request)))
      (GET "/ongoing" [return-url :as request]
        (e-auth-response/bankid-ongoing (:session request) return-url))
      (GET "/no-session" []
        (e-auth-response/bankid-no-session))
      (POST "/collect" [:as request]
        (e-auth-response/bankid-collect (:session request))))))