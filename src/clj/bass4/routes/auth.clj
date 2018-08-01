(ns bass4.routes.auth
  (:require [bass4.layout :as layout]
            [compojure.core :refer [defroutes context GET POST routes]]
            [ring.util.http-response :as http-response]
            [bass4.responses.auth :as auth-response]
            [buddy.hashers :as hashers]))

(defroutes auth-routes
  (GET "/logout" []
    (auth-response/logout))

  (GET "/" [:as request]
    (if (get-in request [:session :identity])
      (http-response/found "/user")
      (http-response/found "/login")))

  (GET "/login" []
    (auth-response/login-page))
  (POST "/login" [username password]
    (auth-response/handle-login username password))

  (POST "/password-hash" [password]
    (layout/text-response (hashers/derive password)))

  (GET "/double-auth" [:as request]
    (auth-response/double-auth (:session request)))
  (POST "/double-auth" [code :as request]
    (auth-response/double-auth-check (:session request) code))

  (GET "/re-auth" [return-url :as request]
    (auth-response/re-auth (:session request) return-url))
  (POST "/re-auth" [password return-url :as request]
    (auth-response/check-re-auth (:session request) password return-url))
  (POST "/re-auth-ajax" [password :as request]
    (auth-response/check-re-auth-ajax (:session request) password))
  (GET "/no-activities" []
    (auth-response/no-activities-page)))