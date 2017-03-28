(ns bass4.routes.auth
  (:require [bass4.layout :as layout]
            [bass4.services.auth :as auth-service]
            [bass4.views.auth :as auth-view]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.http-response :as response]
            [bass4.responses.auth :as auth-response]))

(defroutes auth-routes
  (GET "/login" []
    (auth-view/login-page))
  (POST "/login" [& params :as req]
    (auth-response/handle-login req params))

  (GET "/double-auth" [:as request]
    (auth-response/double-auth-page (:session request)))
  (POST "/double-auth" [& params :as request]
    (auth-response/double-auth-check (:code params) (:session request)))

  (GET "/re-auth" [& params :as request]
    (auth-response/re-auth (:session request) (:return-url params)))
  (POST "/re-auth" [& params :as request]
    (auth-response/check-re-auth (:session request) (:password params) (:return-url params)))
  (POST "/re-auth-ajax" [& params :as request]
    (auth-response/check-re-auth-ajax (:session request) (:password params) ))
  ;; TODO: Make available only in developer mode

  (GET "/timeout" [:as request]
    (-> (response/found "/re-auth")
        (assoc :session
               (merge (:session request)
                      {:auth-timeout true})))))