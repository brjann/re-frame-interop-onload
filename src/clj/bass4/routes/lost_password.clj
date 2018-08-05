(ns bass4.routes.lost-password
  (:require [compojure.core :refer [defroutes context GET POST routes]]
            [bass4.responses.lost-password :as lpw-res]))

(def lost-password-routes
  (context "/lost-password" []
    (GET "/" []
      (lpw-res/lost-password-page))
    (POST "/" [username :as request]
      (lpw-res/handle-username username request))))