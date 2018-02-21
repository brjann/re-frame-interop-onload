(ns bass4.routes.registration
  (:require [bass4.layout :as layout]
            [bass4.services.registration :as reg-service]
            [bass4.utils :refer [str->int]]
            [compojure.core :refer [defroutes context GET POST routes]]
            [ring.util.http-response :as response]))

(defroutes registration-routes
  (GET "/registration/register/:project-id" [project-id :as request]
    (if (reg-service/registration-allowed? (str->int project-id))
      (layout/text-response "OK")
      (layout/text-response "NOT OK"))))