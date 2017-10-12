(ns bass4.routes.home
  (:require [bass4.layout :as layout]
            [bass4.services.auth :as auth]
            [compojure.core :refer [defroutes routes context GET POST ANY]]
            [bass4.db.core :as db]
            [ring.util.http-response :as http-response]
            [ring.util.response :as response]
            [ring.util.request :as request]
            [clojure.java.io :as io]
            [bass4.bass-locals :as locals]
            [bass4.responses.instrument :as instruments]
            [bass4.utils :refer [str->int]]
            [bass4.services.bass :as bass]))

(defn home-page []
  (layout/render
    "home.html" {:docs (-> "docs/docs.md" io/resource slurp)}))

(defn about-page []
  (layout/render
    "about.html"))

(defroutes home-routes
  (GET "/" [] (home-page))
  (GET "/about" [] (about-page)))

