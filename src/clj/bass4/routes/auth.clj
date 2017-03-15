(ns bass4.routes.auth
  (:require [bass4.layout :as layout]
            [bass4.services.auth :as auth-service]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.http-response :as response]
            [bass4.responses.auth :as auth-response]))

;; TODO: Separate into views and responses
(defn login-page []
  (layout/render
    "login.html"))

(defn handle-login [req params]
  (auth-service/login! req params))

(defroutes auth-routes
  (GET "/login" [] (login-page))
  (POST "/login" [& params :as req]
    (handle-login req params))
  (GET "/double-auth" [:as request] (auth-response/double-auth-page (:session request)))
  (POST "/double-auth" [& params :as request] (auth-response/double-auth-check (:code params) (:session request))))