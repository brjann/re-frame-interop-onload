(ns bass4.middleware
  (:require [bass4.env :refer [defaults]]
            [clojure.tools.logging :as log]
            [bass4.layout :refer [*app-context* error-page error-400-page]]
            [bass4.services.bass :as bass]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [bass4.config :refer [env]]
            [bass4.bass-locals :as bass-locals]
            [bass4.utils :refer [filter-map]]
            [ring.middleware.flash :refer [wrap-flash]]
            [immutant.web.middleware :refer [wrap-session]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.backends.session :refer [session-backend]]
            [cprop.tools]
            [clj-time.core :as t]
            [bass4.db.core :as db]
            [bass4.request-state :as request-state])
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


(defn wrap-schema-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch ExceptionInfo e
        (if (= (:type (.data e)) :schema.core/error)
          ;; TODO: Message should only be included in development mode
          (error-400-page (.getMessage e))
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


(defn wrap-ajax-post [handler]
  (fn [request]
    (let [requested-with (get (:headers request) "x-requested-with" "")
          request-method (:request-method request)
          ajax-post? (and (= (clojure.string/lower-case requested-with) "xmlhttprequest")
                         (= request-method :post))
          response (handler request)]
      (if (and ajax-post?
               (= (:status response) 302))
        (let [location (get (:headers response) "Location")
              new-map {:status 200
                       :headers {}
                       :body (str "found " location)}]
          (merge response new-map))
        response))))

;; TODO: Move this to configuration file
(def auth-timeout-limit (* 10 60))

(defn wrap-auth-timeout [handler]
  (fn [request]
    (let [session (:session request)
          now (t/now)
          last-request-time (:last-request-time session)
          auth-timeout (cond
                         ;; TODO: Only randomize timeout when in development mode
                         (= (rand-int 200) 10) (do (log/info "Random timeout")
                                                   true)
                         (:auth-timeout session) true
                         (nil? last-request-time) nil
                         (let [time-elapsed (t/in-seconds (t/interval last-request-time now))]
                           (> time-elapsed auth-timeout-limit)) true
                         :else nil)
          response (handler (assoc-in request [:session :auth-timeout] auth-timeout))
          session-map {:last-request-time now
                       :auth-timeout (if (contains? (:session response) :auth-timeout)
                                         (:auth-timeout (:session response))
                                         auth-timeout)}]
      (assoc response :session (if (nil? (:session response))
                                 (merge session session-map)
                                 (merge (:session response) session-map))))))



#_(defn wrap-request-state [handler]
  (request-state/request-state-wrapper handler))

(defn wrap-request-state [handler]
  (fn [request]
    (request-state/request-state-wrapper handler request)))

(defn wrap-db [handler]
  (fn [request]
    (let [db-config (db/resolve-db request)]
      (binding [db/*db* @(:db-conn db-config)
                bass-locals/*db-config* (cprop.tools/merge-maps bass-locals/db-defaults (filter-map identity db-config))]
        (handler request)))))

;; https://github.com/clojure/clojure/blob/clojure-1.9.0-alpha14/src/clj/clojure/core.clj#L3836
(defn wrap-timer [handler]
  (fn [request]
    (let [time-start (t/now)
          response (handler request)
          diff (t/in-millis (t/interval time-start (t/now)))]
      #_(log/info (str "Render time " diff))
      response)))
;;
;; http://squirrel.pl/blog/2012/04/10/ring-handlers-functional-decorator-pattern/
;; ORDER OF MIDDLEWARE WRAPPERS
;;
;; If wrappers are provided in this order
;; wrap-1
;; wrap-2
;;
;; Then wrapper 2 will call wrapper 1, i.e.,:
;;
;; wrap-2 BEFORE calling handler arg
;; wrap-1 BEFORE calling handler arg
;; wrap-1 AFTER calling handler arg
;; wrap-2 AFTER calling handler arg
;;
;; i.e., wrapper 2 modifies the session going into wrapper 1,
;; and wrapper 1 modifies the response returned to wrapper 2.

(defn wrap-base [handler]
  (-> ((:middleware defaults) handler)
      wrap-db
      wrap-auth-timeout
      wrap-ajax-post
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
      wrap-internal-error
      wrap-request-state
      wrap-timer))