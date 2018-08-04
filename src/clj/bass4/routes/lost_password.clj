(ns bass4.routes.lost-password
  (:require [bass4.layout :as layout]
            [compojure.core :refer [defroutes context GET POST routes]]
            [ring.util.http-response :as http-response]
            [bass4.responses.auth :as auth-response]
            [buddy.hashers :as hashers]))

(def lost-password-routes
  (context "/lost-password" []
    (GET "/" []
      )))