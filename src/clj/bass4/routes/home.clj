(ns bass4.routes.home
  (:require [bass4.layout :as layout]
            [bass4.services.auth :as auth]
            [compojure.core :refer [defroutes GET POST]]
            [bass4.db.core :as db]
            [ring.util.http-response :as response]
            [clojure.java.io :as io]))

(defn home-page []
  (layout/render
    "home.html" {:docs (-> "docs/docs.md" io/resource slurp)}))

(defn login-page []
  (layout/render
    "login.html"))

(defn about-page []
  (layout/render "about.html" {:name "Sven Jansson"}))

(defn handle-login [req params]
  (auth/login! req params))

(defroutes home-routes
  (GET "/" [] (home-page))
  (GET "/about" [] (about-page))
  (GET "/test" [:as req] (str (:session req))))