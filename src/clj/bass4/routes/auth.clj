(ns bass4.routes.auth
  (:require [bass4.layout :as layout]
            [compojure.core :refer [defroutes context GET POST routes]]
            [ring.util.http-response :as http-response]
            [bass4.responses.auth :as auth-response]
            [buddy.hashers :as hashers]))

(defroutes auth-routes
  (GET "/logout" [:as {:keys [session]}]
    (auth-response/logout session))

  (GET "/" [:as request]
    (if (get-in request [:session :user-id])
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

  (GET "/to-activities-finished" []
    (-> (http-response/found "/activities-finished")
        (assoc :session nil)))
  (GET "/activities-finished" []
    (auth-response/activities-finished-page))

  (GET "/to-no-activities" []
    (-> (http-response/found "/no-activities")
        (assoc :session nil)))
  (GET "/no-activities" []
    (auth-response/no-activities-page))

  (GET "/missing-privacy-notice" []
    (layout/text-response "There is no privacy notice in DB or this user's project. User cannot login. Add a privacy notice in admin interface!"))

  (GET "/escalate" []
    (auth-response/escalate-login-page))
  (POST "/escalate" [password :as request]
    (auth-response/handle-escalation (:session request) password)))