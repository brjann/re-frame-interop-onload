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
            [buddy.auth.accessrules :refer [wrap-access-rules]]
            [bass4.env :refer [defaults]]
            [mount.core :as mount]
            [bass4.route-rules :as route-rules]
            [bass4.middleware.core :as middleware :refer [wrap-mw-fn]]
            [bass4.responses.user :as user-response]
            [bass4.clout-cache :as clout-cache]
            [clojure.tools.logging :as log]))

(mount/defstate init-app
  :start ((or (:init defaults) identity))
  :stop ((or (:stop defaults) identity)))

(defn auth-error
  [_ _]
  (layout/error-403-page))

(def compiled-route-middlewares (atom {}))

(defn- compose-routes
  [handler route-mws]
  (loop [v handler route-mws route-mws]
    (if (empty? route-mws)
      v
      (recur ((first route-mws) v) (rest route-mws)))))

(defn wrap-route-mw
  [handler uri & route-mws]
  (fn [request]
    (if (some #(clout-cache/route-matches % request) uri)
      (let [comp-mw (if (contains? @compiled-route-middlewares uri)
                      (get @compiled-route-middlewares uri)
                      (let [comp-mw (compose-routes handler route-mws)]
                        (swap! compiled-route-middlewares assoc uri comp-mw)
                        comp-mw))]
        (comp-mw request))
      (handler request))))

(defn router-middleware
  [handler request]
  ((-> handler
       (wrap-route-mw ["/user*"]
                      (route-rules/wrap-rules user-routes/user-route-rules)
                      #'user-response/treatment-mw
                      #'user-response/check-assessments-mw
                      #'auth-response/auth-re-auth-mw
                      #'middleware/wrap-csrf)
       (wrap-route-mw ["/assessments*"]
                      (route-rules/wrap-rules user-routes/assessment-route-rules)
                      #'user-response/check-assessments-mw
                      #'auth-response/auth-re-auth-mw
                      #'middleware/wrap-csrf))
    request))

(defn route-middlewares-wrapper
  [handler]
  (fn [request]
    (router-middleware handler request)))



(def app-routes
  ;; All routes were wrapped in wrap-formats. I moved that to wrap-base
  (routes
    #'auth-routes
    (-> #'lost-password-routes
        (wrap-access-rules {:rules    lost-password/rules
                            :on-error auth-error}))
    #'user-routes/assessment-routes
    #'user-routes/user-routes
    (-> #'e-auth-routes
        (wrap-routes middleware/wrap-csrf))
    (-> #'embedded-routes
        ;; TODO: Bring back wrap-csrf
        #_(wrap-routes middleware/wrap-csrf))
    #'debug-routes
    (-> #'registration-routes
        (wrap-access-rules {:rules reg-routes/route-rules})
        (wrap-routes middleware/wrap-csrf))
    (-> #'ext-login-routes
        (wrap-routes #(middleware/wrap-mw-fn % ext-login/check-ip-mw)))
    #'quick-login-routes
    ;; Replacement for route/not-found
    (layout/route-not-found)))


(defn app [] (middleware/wrap-base (route-middlewares-wrapper #'app-routes)))
