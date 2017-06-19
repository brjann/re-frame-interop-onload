(ns bass4.routes.auth
  (:require [bass4.layout :as layout]
            [bass4.services.auth :as auth-service]
            [bass4.views.auth :as auth-view]
            [compojure.core :refer [defroutes context GET POST routes]]
            [ring.util.http-response :as response]
            [bass4.responses.auth :as auth-response]
            [bass4.services.user :as user]
            [bass4.request-state :as request-state]))

(defroutes auth-routes
  (GET "/logout" [& params]
    (auth-response/logout))

  (GET "/login" []
    (auth-view/login-page))
  (POST "/login" [& params :as request]
    (auth-response/handle-login (:session request) (:username params) (:password params)))

  (GET "/double-auth" [:as request]
    (auth-response/double-auth (:session request)))
  (POST "/double-auth" [& params :as request]
    (auth-response/double-auth-check (:session request) (:code params)))

  (GET "/re-auth" [& params :as request]
    (auth-response/re-auth (:session request) (:return-url params)))
  (POST "/re-auth" [& params :as request]
    (auth-response/check-re-auth (:session request) (:password params) (:return-url params)))
  (POST "/re-auth-ajax" [& params :as request]
    (auth-response/check-re-auth-ajax (:session request) (:password params) )))