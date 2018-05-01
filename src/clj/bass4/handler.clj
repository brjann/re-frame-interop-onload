(ns bass4.handler
  (:require [compojure.core :refer [routes wrap-routes]]
            [bass4.layout :refer [error-page] :as layout]
            [bass4.routes.auth :refer [auth-routes]]
            [bass4.routes.user :refer [user-routes]]
            [bass4.routes.embedded :refer [embedded-routes]]
            [bass4.routes.registration :refer [registration-routes] :as reg-routes]
            [bass4.routes.ext-login :refer [ext-login-routes] :as ext-login]
            [bass4.routes.quick-login :refer [quick-login-routes]]
            [bass4.routes.debug :refer [debug-routes]]
            [compojure.route :as route]
            [bass4.env :refer [defaults]]
            [mount.core :as mount]
            [bass4.middleware.core :as middleware]
            [bass4.middleware.errors :refer [wrap-schema-error wrap-restricted]]))

(mount/defstate init-app
                :start ((or (:init defaults) identity))
                :stop  ((or (:stop defaults) identity)))

(def app-routes
  (routes
    (-> #'auth-routes
        (wrap-routes middleware/wrap-formats)
        (wrap-routes wrap-schema-error))
    (-> #'user-routes
        ;; (wrap-routes middleware/wrap-auth-re-auth)
        (wrap-routes #(middleware/wrap-mw-fn % middleware/auth-re-auth-wrapper))
        (wrap-routes #(middleware/wrap-mw-fn % ext-login/return-url-mw))
        (wrap-routes middleware/wrap-csrf)
        (wrap-routes middleware/wrap-formats)
        (wrap-routes wrap-restricted)
        (wrap-routes wrap-schema-error))
    (-> #'embedded-routes
        ;; TODO: Bring back wrap-csrf
        #_(wrap-routes middleware/wrap-csrf)
        (wrap-routes middleware/wrap-formats)
        (wrap-routes wrap-schema-error))
    (-> #'debug-routes
        (wrap-routes middleware/wrap-formats))
    (-> #'registration-routes
        (wrap-routes middleware/wrap-formats)
        (wrap-routes #(middleware/wrap-mw-fn % reg-routes/captcha-mw)))
    (-> #'ext-login-routes
        (wrap-routes #(middleware/wrap-mw-fn % ext-login/check-ip-mw))
        (wrap-routes middleware/wrap-formats))
    (-> #'quick-login-routes
        (wrap-routes middleware/wrap-formats))
    ;; Replacement for route/not-found
    (layout/route-not-found)))

(defn app [] (middleware/wrap-base #'app-routes))
