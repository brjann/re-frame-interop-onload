(ns bass4.routes.home
  (:require [bass4.layout :as layout]
            [bass4.services.auth :as auth]
            [compojure.core :refer [defroutes GET POST]]
            [bass4.db.core :as db]
            [ring.util.http-response :as response]
            [ring.util.request :as request]
            [clojure.java.io :as io]
            [bass4.bass-locals :as locals]
            [bass4.responses.instrument :as instruments]))

(defn home-page []
  (layout/render
    "home.html" {:docs (-> "docs/docs.md" io/resource slurp)}))

(defn login-page []
  (layout/render
    "login.html"))

(defn about-page []
  (layout/render
    "about.html"))

(defroutes home-routes
  (GET "/" [] (home-page))
  (GET "/instrument/post/:instrument-id" [instrument-id & params]
    (instruments/post-answers instrument-id (:answers params)))
  (GET "/instrument/:instrument-id" [instrument-id &params]
    (instruments/instrument-page instrument-id))
  (GET "/about" [] (about-page))
  (GET "/session" [:as req] (str (:session req)))
  (GET "/request" [:as req] (str req))
  (GET "/test" [:as req] (str (:server-name req))))