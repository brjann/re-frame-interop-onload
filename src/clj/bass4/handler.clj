(ns bass4.handler
  (:require [compojure.core :refer [routes wrap-routes]]
            [bass4.layout :refer [error-page] :as layout]
            [bass4.routes.auth :refer [auth-routes]]
            [bass4.responses.auth :as auth-response]
            [bass4.routes.user :as user-routes]
            [bass4.routes.embedded :refer [embedded-routes]]
            [bass4.routes.registration :refer [registration-routes] :as reg-routes]
            [bass4.routes.ext-login :refer [ext-login-routes] :as ext-login]
            [bass4.routes.quick-login :refer [quick-login-routes]]
            [bass4.routes.debug :refer [debug-routes]]
            [bass4.routes.e-auth :refer [e-auth-routes]]
            [bass4.routes.lost-password :refer [lost-password-routes] :as lost-password]
            [bass4.env :refer [defaults]]
            [mount.core :as mount]
            [bass4.middleware.core :as middleware :refer [wrap-mw-fn]]
            [clojure.tools.logging :as log]))

(mount/defstate init-app
  :start ((or (:init defaults) identity))
  :stop ((or (:stop defaults) identity)))

(defn auth-error
  [_ _]
  (layout/error-403-page))

(defn router-middleware
  [handler request]
  ((-> handler
       user-routes/user-routes-mw
       user-routes/assessment-routes-mw
       user-routes/ajax-user-routes-mw
       reg-routes/registration-routes-mw
       user-routes/privacy-consent-mw
       lost-password/lpw-routes-mw)
    request))

(defn route-middlewares-wrapper
  [handler]
  (fn [request]
    (router-middleware handler request)))



(def app-routes
  ;; All routes were wrapped in wrap-formats. I moved that to wrap-base
  (routes
    #'auth-routes
    #'lost-password-routes
    #'user-routes/assessment-routes
    #'user-routes/user-routes
    #'user-routes/privacy-consent-routes
    #'user-routes/ajax-user-routes
    (-> #'e-auth-routes
        (wrap-routes middleware/wrap-csrf))
    (-> #'embedded-routes
        ;; TODO: Bring back wrap-csrf
        #_(wrap-routes middleware/wrap-csrf))
    #'debug-routes
    #'registration-routes
    (-> #'ext-login-routes
        (wrap-routes #(middleware/wrap-mw-fn % ext-login/check-ip-mw)))
    #'quick-login-routes
    ;; Replacement for route/not-found
    (layout/route-not-found)))


(defn app [] (middleware/wrap-base (route-middlewares-wrapper #'app-routes)))
