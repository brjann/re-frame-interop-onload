(ns bass4.handler
  (:require [compojure.core :refer [routes wrap-routes]]
            [bass4.layout :refer [error-page] :as layout]
            [bass4.routes.home :refer [home-routes]]
            [bass4.routes.auth :refer [auth-routes]]
            [bass4.routes.user :refer [user-routes]]
            [bass4.routes.debug :refer [debug-routes]]
            [compojure.route :as route]
            [bass4.env :refer [defaults]]
            [mount.core :as mount]
            [bass4.middleware :as middleware]))

(mount/defstate init-app
                :start ((or (:init defaults) identity))
                :stop  ((or (:stop defaults) identity)))

(def app-routes
  (routes
    (-> #'auth-routes
        (wrap-routes middleware/wrap-formats)
        (wrap-routes middleware/wrap-schema-error))
    (-> #'home-routes
        (wrap-routes middleware/wrap-csrf)
        (wrap-routes middleware/wrap-formats)
        (wrap-routes middleware/wrap-schema-error))
    (-> #'user-routes
        (wrap-routes middleware/wrap-csrf)
        (wrap-routes middleware/wrap-formats)
        (wrap-routes middleware/wrap-restricted)
        (wrap-routes middleware/wrap-schema-error))
    (-> #'debug-routes
        (wrap-routes middleware/wrap-formats))
    (route/not-found
      (:body
        #_(layout/error-404-page)
        (error-page {:status 404
                     :title "page not found"})))))


(defn app [] (middleware/wrap-base #'app-routes))
