(ns bass4.routes.auth
  (:require [bass4.layout :as layout]
            [bass4.services.auth :as auth]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.http-response :as response]))


(defn login-page []
  (layout/render
    "login.html"))

(defn handle-login [req params]
  (auth/login! req params))

(defroutes auth-routes
  (GET "/login" [] (login-page))
  (POST "/login" [& params :as req]
    (handle-login req params)))