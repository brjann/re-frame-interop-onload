(ns bass4.routes.e-auth
  (:require [compojure.core :refer [defroutes context GET POST routes]]
            [bass4.responses.e-auth :as e-auth-response]))

(defroutes e-auth-routes
  (context "/e-auth" []
    (context "/bankid" []
      (GET "/status" [:as request]
        (e-auth-response/bankid-status-page (:session request)))
      (GET "/cancel" [return-url :as request]
        (e-auth-response/bankid-cancel (:session request) return-url))
      (GET "/reset" [:as request]
        (e-auth-response/bankid-reset (:session request)))
      (GET "/ongoing" [return-url :as request]
        (e-auth-response/bankid-ongoing (:session request) return-url))
      (GET "/no-session" []
        (e-auth-response/bankid-no-session))
      (POST "/collect" [:as request]
        (e-auth-response/bankid-collect (:session request))))))