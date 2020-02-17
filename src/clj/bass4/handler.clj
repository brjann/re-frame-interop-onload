(ns bass4.handler
  (:require [compojure.core :refer [routes wrap-routes]]
            [mount.core :as mount]
            [clojure.tools.logging :as log]
            [ring.util.http-response :as http-response]
            [bass4.routes.auth :refer [auth-routes]]
            [bass4.routes.user :as user-routes]
            [bass4.embedded.routes :refer [embedded-routes]]
            [bass4.registration.routes :refer [registration-routes] :as reg-routes]
            [bass4.routes.ext-login :refer [ext-login-routes] :as ext-login]
            [bass4.routes.quick-login :as quick-login]
            [bass4.routes.debug :refer [debug-routes]]
            [bass4.routes.e-auth :refer [e-auth-routes]]
            [bass4.lost-password.routes :as lost-password]
            [bass4.external-messages.sms-status :as sms-status]
            [bass4.env :refer [defaults]]
            [bass4.middleware.core :as middleware :refer [wrap-mw-fn]]
            [bass4.routes.api :as api]
            [bass4.embedded.api :as embedded-api]))

(mount/defstate init-app
  :start ((or (:init defaults) identity))
  :stop ((or (:stop defaults) identity)))

(defn router-middleware
  [handler request]
  ((-> handler
       embedded-api/api-tx-routes-mw
       api/api-tx-routes-mw
       api/swagger-mw
       user-routes/user-tx-routes-mw
       user-routes/assessment-routes-mw
       user-routes/root-reroute-mw
       user-routes/user-routes-mw
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
    #'sms-status/route
    #'api/api-routes
    #'auth-routes
    #'lost-password/lost-password-routes
    #'user-routes/pluggable-ui
    #'user-routes/assessment-routes
    #'user-routes/root-reroute
    #'user-routes/tx-routes
    #'user-routes/privacy-consent-routes
    (-> #'e-auth-routes
        (wrap-routes middleware/wrap-csrf))
    (-> #'embedded-routes
        ;; TODO: Bring back wrap-csrf
        #_(wrap-routes middleware/wrap-csrf))
    #'debug-routes
    #'registration-routes
    (-> #'ext-login-routes
        (wrap-routes #(middleware/wrap-mw-fn % ext-login/check-ip-mw)))
    #'quick-login/quick-login-routes
    ;; Replacement for route/not-found
    (fn [request]
      (http-response/not-found)
      #_(let [body (error-404-page)]
          (-> (response/render body request)
              (http-response/status 404)
              (cond-> (= (:request-method request) :head) (assoc :body nil)))))))


(defn app [] (middleware/wrap-base (route-middlewares-wrapper #'app-routes)))
