(ns bass4.handler
  (:require [compojure.core :refer [routes wrap-routes]]
            [bass4.layout :refer [error-page] :as layout]
            [bass4.routes.auth :refer [auth-routes]]
            [bass4.responses.auth :as auth-res]
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
            [bass4.middleware.core :as middleware]
            [bass4.responses.user :as user-response]
            [bass4.clout-cache :as clout-cache]))

(mount/defstate init-app
  :start ((or (:init defaults) identity))
  :stop ((or (:stop defaults) identity)))

(defn auth-error
  [_ _]
  (layout/error-403-page))

(defn wrap-route-mw
  [handler routes route-mw]
  (fn [request]
    (if (some #(clout-cache/route-matches % request) routes)
      (route-mw handler request)
      (handler request))))

(defn route-middlewares
  [handler]
  (-> handler
      (wrap-route-mw ["/user*"] (route-rules/wrap-rules2 user-routes/user-route-rules))
      (wrap-route-mw ["/user*"] #'user-response/treatment-mw)
      (wrap-route-mw ["/user*"] #'user-response/check-assessments-mw)
      (wrap-route-mw ["/assessments*"] (route-rules/wrap-rules2 user-routes/assessment-route-rules))
      (wrap-route-mw ["/assessments*"] #'user-response/check-assessments-mw)))

(def app-routes
  (routes
    #_(->
        (wrap-routes (fn [handler]
                       (fn [request]
                         (handler (assoc request :fuck :me))))))
    (-> #'auth-routes
        (wrap-routes middleware/wrap-formats))
    (-> #'lost-password-routes
        (wrap-access-rules {:rules    lost-password/rules
                            :on-error auth-error})
        (wrap-routes middleware/wrap-formats))
    #_(-> #'user-routes/privacy-consent-routes
          (wrap-routes #(middleware/wrap-mw-fn % auth-res/auth-re-auth-wrapper))
          (wrap-routes #(middleware/wrap-mw-fn % ext-login/return-url-mw))
          (wrap-routes middleware/wrap-csrf)
          (wrap-routes middleware/wrap-formats)
          (wrap-routes wrap-restricted))
    (-> #'user-routes/assessment-routes
        ;; TODO: Move back here
        #_(wrap-routes (route-rules/wrap-rules user-routes/assessment-route-rules))
        #_(wrap-routes #(middleware/wrap-mw-fn % user-response/privacy-consent-mw))
        (wrap-routes #(middleware/wrap-mw-fn % auth-res/auth-re-auth-wrapper))
        #_(wrap-routes #(middleware/wrap-mw-fn % user-response/check-assessments-mw))
        #_(wrap-routes #(middleware/wrap-mw-fn % ext-login/return-url-mw))
        (wrap-routes middleware/wrap-csrf)
        (wrap-routes middleware/wrap-formats))
    (-> #'user-routes/user-routes
        ;; TODO: Move back here
        #_(wrap-routes (route-rules/wrap-rules user-routes/user-route-rules))
        #_(wrap-routes #(middleware/wrap-mw-fn % user-response/treatment-mw))
        #_(wrap-routes #(middleware/wrap-mw-fn % user-response/privacy-consent-mw))
        (wrap-routes #(middleware/wrap-mw-fn % auth-res/auth-re-auth-wrapper))
        #_(wrap-routes #(middleware/wrap-mw-fn % user-response/check-assessments-mw))
        #_(wrap-routes #(middleware/wrap-mw-fn % ext-login/return-url-mw))
        (wrap-routes middleware/wrap-csrf)
        (wrap-routes middleware/wrap-formats))
    (-> #'e-auth-routes
        (wrap-routes middleware/wrap-csrf)
        (wrap-routes middleware/wrap-formats))
    (-> #'embedded-routes
        ;; TODO: Bring back wrap-csrf
        #_(wrap-routes middleware/wrap-csrf)
        (wrap-routes middleware/wrap-formats))
    (-> #'debug-routes
        (wrap-routes middleware/wrap-formats))
    (-> #'registration-routes
        (wrap-access-rules {:rules reg-routes/route-rules})
        (wrap-routes middleware/wrap-csrf)
        (wrap-routes middleware/wrap-formats))
    (-> #'ext-login-routes
        (wrap-routes #(middleware/wrap-mw-fn % ext-login/check-ip-mw))
        (wrap-routes middleware/wrap-formats))
    (-> #'quick-login-routes
        (wrap-routes middleware/wrap-formats))
    ;; Replacement for route/not-found
    (layout/route-not-found)))

(defn app [] (middleware/wrap-base (-> #'app-routes
                                       route-middlewares)))
