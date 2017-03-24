(ns bass4.middleware
  (:require [bass4.env :refer [defaults]]
            [clojure.tools.logging :as log]
            [bass4.layout :refer [*app-context* error-page]]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [bass4.config :refer [env]]
            [ring.middleware.flash :refer [wrap-flash]]
            [immutant.web.middleware :refer [wrap-session]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.backends.session :refer [session-backend]]
            [clj-time.core :as t])
  (:import [javax.servlet ServletContext]
           (clojure.lang ExceptionInfo)))

(defn wrap-context [handler]
  (fn [request]
    (binding [*app-context*
              (if-let [context (:servlet-context request)]
                ;; If we're not inside a servlet environment
                ;; (for example when using mock requests), then
                ;; .getContextPath might not exist
                (try (.getContextPath ^ServletContext context)
                     (catch IllegalArgumentException _ context))
                ;; if the context is not specified in the request
                ;; we check if one has been specified in the environment
                ;; instead
                (:app-context env))]
      (handler request))))

(defn wrap-internal-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (log/error t)
        (error-page {:status 500
                     :title "Something very bad has happened!"
                     :message "We've dispatched a team of highly trained gnomes to take care of the problem."})))))

(defn wrap-csrf [handler]
  (wrap-anti-forgery
    handler
    {:error-response
     (error-page
       {:status 403
        :title "Invalid anti-forgery token"})}))

(defn wrap-formats [handler]
  (let [wrapped (wrap-restful-format
                  handler
                  {:formats [:json-kw :transit-json :transit-msgpack]})]
    (fn [request]
      ;; disable wrap-formats for websockets
      ;; since they're not compatible with this middleware
      ((if (:websocket? request) handler wrapped) request))))

(defn on-error [request response]
  (error-page
    {:status 403
     :title (str "Access to " (:uri request) " is not authorized")}))

(defn wrap-restricted [handler]
  (restrict handler {:handler authenticated?
                     :on-error on-error}))

(defn wrap-auth [handler]
  (let [backend (session-backend)]
    (-> handler
        (wrap-authentication backend)
        (wrap-authorization backend))))



;; ----------------
;;  BASS4 handlers
;; ----------------

; http://squirrel.pl/blog/2012/04/10/ring-handlers-functional-decorator-pattern/

(defn wrap-schema-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch ExceptionInfo e
        (if (= (:type (.data e)) :schema.core/error)
          (error-page {:status 400
                       :title "Bad request!"
                       :message (.getMessage e)})
          (throw e))))))

;; TODO: This should not be applied to js and css-files
;; TODO: http://stackoverflow.com/questions/8861181/clear-all-fields-in-a-form-upon-going-back-with-browser-back-button
(defn wrap-reload-headers [handler]
  (fn [request]
    (let [response (handler request)]
      (update-in response
                 [:headers] #(assoc %1
                               "Cache-Control" "no-cache, no-store, must-revalidate"
                               "Expires" "Mon, 26 Jul 1997 05:00:00 GMT"
                               "Pragma" "no-cache")))))


(def auth-timeout 20)

(defn wrap-auth-timeout [handler]
  (fn [request]
    (handler
      (if (get-in request [:session :identity])
        (if (get-in request [:session :auth-timeout])
          request
          (let [last-request-time (get-in request [:session :last-request-time])]
            (if (nil? last-request-time)
              (assoc-in request [:session :last-request-time] (t/now))
              (let [time-elapsed (t/in-seconds (t/interval last-request-time (t/now)))]
                (log/error (str "time elapsed" time-elapsed))
                (if (> time-elapsed auth-timeout)
                  (-> request
                      ;; TODO: Remove key instead of setting to nil
                      (assoc-in [:session :last-request-time] nil)
                      (assoc-in [:session :auth-timeout] true))
                  (assoc-in request [:session :last-request-time] (t/now)))))))
        request))))

(defn wrap-base [handler]
  (-> ((:middleware defaults) handler)
      wrap-auth-timeout
      wrap-auth
      wrap-webjars
      wrap-flash
      (wrap-session {:cookie-attrs {:http-only true}})
      (wrap-defaults
        (-> site-defaults
            (assoc-in [:security :anti-forgery] false)
            (dissoc :session)))
      wrap-reload-headers
      wrap-context
      wrap-internal-error))