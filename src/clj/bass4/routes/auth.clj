(ns bass4.routes.auth
  (:require [bass4.layout :as layout]
            [bass4.services.auth :as auth-service]
            [bass4.views.auth :as auth-view]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.http-response :as response]
            [bass4.responses.auth :as auth-response]))

(defroutes auth-routes
  ;; By-pass of response. OK?
  (GET "/login" []
    (auth-view/login-page))
  (POST "/login" [& params :as req]
    (auth-response/handle-login req params))
  (GET "/double-auth" [:as request]
    (auth-response/double-auth-page (:session request)))
  (POST "/double-auth" [& params :as request]
    (auth-response/double-auth-check (:code params) (:session request)))
  (GET "/re-auth" [:as request]
    (auth-response/re-auth (:session request)))
  #_(POST "/re-auth" [&params :as request]
    (POST "*" [:as request] (-> (response/found "/user/")
                                (assoc :session (merge (:session request) {:auth-timeout nil})))))
  (POST "/re-auth" [& params :as request]
    (auth-response/check-re-auth (:session request) (:password params) ))
  (POST "/re-auth-ajax" [:as request]
    (POST "*" [:as request] (-> (response/ok)
                                (assoc :session (merge (:session request) {:auth-timeout nil})))))
  ;; TODO: Make available only in developer mode
  (GET "/timeout" [:as request]
    (-> (response/found "/re-auth")
        (assoc :session
               (merge (:session request)
                      {:auth-timeout true})))))