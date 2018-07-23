(ns bass4.routes.auth
  (:require [bass4.layout :as layout]
            [bass4.services.auth :as auth-service]
            [compojure.core :refer [defroutes context GET POST routes]]
            [ring.util.http-response :as response]
            [bass4.responses.auth :as auth-response]
            [bass4.services.user :as user]
            [bass4.request-state :as request-state]
            [buddy.hashers :as hashers]))

(defroutes auth-routes
  (GET "/logout" [& params]
    (auth-response/logout))

  (GET "/" [:as request]
    (if (get-in request [:session :identity])
      (response/found "/user")
      (response/found "/login")))

  (GET "/login" []
    (auth-response/login-page))
  (POST "/login" [& params :as request]
    (auth-response/handle-login request (:username params) (:password params)))

  (POST "/password-hash" [& params]
    (layout/text-response (hashers/derive (:password params))))

  (GET "/double-auth" [:as request]
    (auth-response/double-auth (:session request)))
  (POST "/double-auth" [& params :as request]
    (auth-response/double-auth-check (:session request) (:code params)))

  (GET "/re-auth" [& params :as request]
    (auth-response/re-auth (:session request) (:return-url params)))
  (POST "/re-auth" [& params :as request]
    (auth-response/check-re-auth (:session request) (:password params) (:return-url params)))
  (POST "/re-auth-ajax" [& params :as request]
    (auth-response/check-re-auth-ajax (:session request) (:password params)))
  (GET "/no-activities" [& params :as request]
    (auth-response/no-activities-page)))