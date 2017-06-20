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
            [bass4.utils :refer [filter-map time+ nil-zero?]]
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
            [bass4.config :refer [env]]
            [bass4.mailer :refer [mail!]]
            [clojure.string]
            [bass4.request-state :as request-state]
            [clj-time.coerce :as tc]
            [prone.middleware :refer [wrap-exceptions]])
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

(def ^:dynamic *skip-csrf* false)
(defn csrf-wrapper
  [handler request]
  (if *skip-csrf*
    (handler request)
    ((wrap-anti-forgery
       handler
       {:error-response
        (error-page
          {:status 403
           :title  "Invalid anti-forgery token"})}) request)))

(defn wrap-csrf [handler]
    (fn [request]
      (csrf-wrapper handler request)))

#_(defn wrap-csrf [handler]
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
;;
;;
;;
;;  BASS4 HANDLERS
;;
;;
;;
;; ----------------


(defn internal-error-wrapper
  [handler req]
  (try
    (handler req)
    (catch Throwable t
      (log/error t)
      (request-state/record-error! t)
      (error-page {:status 500
                   :title "Something very bad has happened!"
                   :message "We've dispatched a team of highly trained gnomes to take care of the problem."}))))


(defn wrap-internal-error [handler]
  (fn [req]
    (internal-error-wrapper handler req)))



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



;; ----------------
;;  AJAX POST
;; ----------------

(defn- is-ajax-post?
  [request]
  (let [requested-with (get (:headers request) "x-requested-with" "")
        request-method (:request-method request)]
    (and (= (clojure.string/lower-case requested-with) "xmlhttprequest")
         (= request-method :post))))

(defn- ajax-found
  [response]
  (let [location (get (:headers response) "Location")
        new-map  {:status  200
                  :headers {}
                  :body    (str "found " location)}]
    (merge response new-map)))

(defn- ajax-403-logged-in
  [request response]
  ;; Do not send email about this strange error
  (request-state/record-error!
    (str "403 error when posting to " (:uri request)
         "\nUser " (get-in request [:session :identity])))
  (merge response {:status  403
                   :headers {}
                   :body    "reload"}))

(defn- ajax-403-not-logged-in
  [response]
  (merge response {:status  403
                   :headers {}
                   :body    "login"}))

(defn ajax-post-wrapper
  [handler request]
  (let [ajax-post? (is-ajax-post? request)
        response (handler request)]
    (cond

      ;; if ajax and response is 302, then send
      ;; the special found location response instead
      (and ajax-post? (= (:status response) 302)) (ajax-found response)

      ;; The user is trying to post to
      ;; forbidden but is not logged out.
      ;; Save this as an error
      (and ajax-post?
           (= (:status response) 403)
           (not= nil (get-in request [:session :identity])))
      (ajax-403-logged-in request response)

      (and ajax-post? (= (:status response) 403))
      (ajax-403-not-logged-in response)

      :else
      response)))

(defn wrap-ajax-post [handler]
  (fn [request]
    (ajax-post-wrapper handler request)))



(defn auth-timeout-wrapper
  [handler request]
  (let [session (:session request)
        now (t/now)
        last-request-time (:last-request-time session)
        auth-timeout-limit (or (env :timeout-soft) (* 30 60))
        auth-timeout (cond
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
                               (merge (:session response) session-map)))))

(defn wrap-auth-timeout [handler]
  (fn [request]
    (auth-timeout-wrapper handler request)))


;; ----------------
;;  REQUEST STATE
;; ----------------

(defn mail-error!
  [req-state]
  (if-not (env :dev-test)
    (try
      (mail!
        (env :email-error)
        "Error in BASS4"
        (str "Sent by " (:name req-state) "\n" (:error-messages req-state)))
      (catch Exception x
        (log/error "Could not send error email to: " (env :email-error) "\nError: " x)))
    (log/info "No emails in test mode")))

(defn save-log!
  [req-state request time]
  (db/save-pageload! {:db-name         (:name req-state),
                      :remote-ip       (:remote-addr request),
                      :sql-time        (when (:sql-times req-state)
                                         (/ (apply + (:sql-times req-state)) 1000)),
                      :sql-max-time    (when (:sql-times req-state)
                                         (/ (apply max (:sql-times req-state)) 1000)),
                      :user-id         (:user-id req-state),
                      :render-time     (/ time 1000),
                      :response-size   (count (:body val)),
                      :clojure-version (str "Clojure " (clojure-version)),
                      :error-count     (:error-count req-state)
                      :error-messages  (:error-messages req-state)
                      :source-file     (:uri request),
                      :session-start   (tc/to-epoch (:session-start req-state)),
                      :user-agent      (get-in request [:headers "user-agent"])}))

;; I would like to place this in the request-state namespace, however
;; that creates a circular dependency because db also uses the request-state
;; Don't really know how to handle that...
(defn request-state-wrapper
  [handler request]
  (binding [request-state/*request-state* (atom {})]
    (let [{:keys [val time]} (time+ (handler request))
          req-state (request-state/get-state)]
      ;; Only save if request is tied to specific database
      (when (:name req-state)
        ;; Email errors
        (when-not (nil-zero? (:error-count req-state))
          (mail-error! req-state))
        (save-log! req-state request time))
      val)))

(defn wrap-request-state [handler]
  (fn [request]
    (request-state-wrapper handler request)))

(defn wrap-db [handler]
  (fn [request]
    (db/db-wrapper handler request)))

(defn wrap-debug-exceptions
  [handler]
  (fn [request]
    (if (or (env :debug-mode) (env :dev))
      ((wrap-exceptions handler) request)
      (handler request))))


;; I tried to wrap around immutant.web.middleware/wrap-session
;; but it did not work. Worked in browser but not tests.
;; So extra wrapper instead
(defn session-state-wrapper
  [handler request]
  (let [session (:session request)]
    (request-state/set-state! :user-id (:identity session))
    (request-state/set-state! :session-start (:session-start session)))
  (handler request))

(defn wrap-session-state
  [handler]
  (fn [request]
    (session-state-wrapper handler request)))

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
      ;wrap-exceptions
      wrap-debug-exceptions
      wrap-db
      wrap-auth-timeout
      wrap-ajax-post
      wrap-auth
      wrap-webjars
      wrap-flash
      wrap-session-state
      ;; Default absolute time-out to 2 hours
      (wrap-session {:cookie-attrs {:http-only true} :timeout (or (env :timeout-hard) (* 120 60))})
      (wrap-defaults
        (-> site-defaults
            (assoc-in [:security :anti-forgery] false)
            (dissoc :session)))
      ;; TODO: Place before webjars or even further up
      wrap-reload-headers
      wrap-context
      wrap-internal-error
      wrap-request-state))