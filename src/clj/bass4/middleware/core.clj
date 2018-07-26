(ns bass4.middleware.core
  (:require [compojure.core :refer [defroutes context GET POST ANY routes]]
            [bass4.env :refer [defaults]]
            [clojure.tools.logging :as log]
            [bass4.layout :refer [*app-context* error-page error-400-page]]
            [bass4.services.bass :as bass]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [bass4.utils :refer [filter-map time+ nil-zero? fnil+]]
            [ring.middleware.flash :refer [wrap-flash]]
            [immutant.web.middleware :refer [wrap-session]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults secure-site-defaults]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]
            [buddy.auth.backends.session :refer [session-backend]]
            [cprop.tools]
            [clj-time.core :as t]
            [bass4.db.core :as db]
            [bass4.config :refer [env]]
            [bass4.mailer :refer [mail!]]
            [bass4.sms-sender :as sms]
            [bass4.request-state :as request-state]
            [bass4.layout :as layout]
            [bass4.services.user :as user]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [bass4.middleware.debug :refer [debug-redefs wrap-debug-exceptions wrap-session-modification]]
            [bass4.middleware.request-state :refer [request-state]]
            [bass4.middleware.ajax-post :refer [ajax-post]]
            [bass4.middleware.embedded :refer [embedded-mw embedded-iframe]]
            [bass4.middleware.errors :refer [internal-error]]
            [ring.util.http-response :as response]
            [ring.util.response :as response-utils]
            [ring.util.mime-type :as mime-type]
            [bass4.responses.auth :as auth-response]
            [bass4.middleware.file-php :as file-php]
            [bass4.services.attack-detector :as a-d]
            [bass4.responses.e-auth :as e-auth])

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


(defn wrap-formats [handler]
  (let [wrapped (wrap-restful-format
                  handler
                  {:formats [:json-kw :transit-json :transit-msgpack]})]
    (fn [request]
      ;; disable wrap-formats for websockets
      ;; since they're not compatible with this middleware
      ((if (:websocket? request) handler wrapped) request))))

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


(defn wrap-mw-fn
  [handler middleware]
  (fn [request]
    (middleware handler request)))

(defn wrap-reload-headers [handler]
  (fn [request]
    (let [response (handler request)]
      (update-in response
                 [:headers] #(assoc %1
                               "Cache-Control" "no-cache, no-store, must-revalidate"
                               "Expires" "Wed, 12 Jul 1978 08:00:00 GMT"
                               "Pragma" "no-cache")))))



;; -----------------
;;    CSRF ERROR
;; -----------------


(defn- csrf-error
  ;; Takes request as argument but underscored to avoid unused variable type hint
  [_]
  (request-state/add-to-state-key! :info "CSRF error")
  (error-page
    {:status 403
     :title  "Invalid anti-forgery token"}))

(def ^:dynamic *skip-csrf* false)
(defn csrf-wrapper
  [handler request]
  (if *skip-csrf*
    (handler request)
    ((wrap-anti-forgery
       handler
       {:error-handler csrf-error}) request)))

(defn wrap-csrf [handler]
  (fn [request]
    (csrf-wrapper handler request)))


;; -----------------
;;  RE-AUTHENTICATE
;; -----------------



(defn user-identity
  [handler request]
  "Check if user in identity exists
    yes: add user map to session
    no: remove :identity key from from session
    Also adds some request state info"
  (request-state/set-state! :session-cookie (get-in request [:cookies "JSESSIONID" :value]
                                                    (get-in request [:cookies "ring-session" :value])))
  (let [assessments-pending-pre?  (get-in request [:session :assessments-pending?])
        res                       (handler (if-let [user (user/get-user (:identity request))]
                                             (assoc-in request [:session :user] user)
                                             (merge request {:identity nil :session (dissoc (:session request) :identity)})))
        assessments-pending-post? (when (contains? res :session)
                                    (true? (get-in res [:session :assessments-pending?])))]
    (cond
      assessments-pending-post?
      (request-state/add-to-state-key! :info "Assessments pending")

      (and assessments-pending-pre? (false? assessments-pending-post?))
      (request-state/add-to-state-key! :info "Assessments completed")

      assessments-pending-pre?
      (request-state/add-to-state-key! :info "Assessments pending"))
    res))

;; I tried to wrap around immutant.web.middleware/wrap-session
;; but it did not work. Worked in browser but not tests.
;;
;; This code does not work in tests
;(defn session-cookie-wrapper
;  [handler request]
;  ;; Default absolute time-out to 2 hours
;  (let [session-handler (wrap-session handler {:cookie-attrs {:http-only true} :timeout (or (env :timeout-hard) (* 120 60))})]
;    (session-handler request)))
;
;(defn wrap-session-cookie
;  [handler]
;  (fn [request]
;    (session-cookie-wrapper handler request)))
;
; Failing test using kerodon
;(get-in (-> (session (app))
;            (visit "/debug/set-session" :params {:identity 549821 :double-authed 1})
;            (visit "/debug/session")) [:response :body])

;; So extra wrapper instead
(defn request-state-session-info
  [handler request]
  (let [session (:session request)]
    (request-state/set-state! :user-id (:identity session))
    (request-state/set-state! :session-start (:session-start session)))
  (handler request))


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
;; i.e., wrapper 2 modifies the request going into wrapper 1,
;; and wrapper 1 modifies the response returned to wrapper 2.

(defn wrap-base [handler]
  (-> ((:middleware defaults) handler)
      ;wrap-exceptions
      ;wrap-auth-re-auth
      ;; Must quote the names of the functions.
      ;; Else the actual functions are passed as arguments
      (wrap-mw-fn #'e-auth/bankid-middleware)
      (wrap-mw-fn #'user-identity)
      wrap-debug-exceptions
      (wrap-mw-fn #'embedded-mw)
      (wrap-mw-fn #'file-php/File-php)
      (wrap-mw-fn #'db/db-middleware)
      (wrap-mw-fn #'a-d/attack-detector-mw)
      (wrap-mw-fn #'ajax-post)
      wrap-auth
      wrap-reload-headers
      wrap-webjars
      wrap-flash
      (wrap-mw-fn #'request-state-session-info)
      wrap-session-modification
      ;; Default absolute time-out to 2 hours
      (wrap-session {:cookie-attrs {:http-only true} :timeout (or (env :timeout-hard) (* 120 60))})
      (wrap-defaults
        (->
          ;; TODO: This results in eternal loop. Although it should not.
          ;; https://github.com/ring-clojure/ring-defaults#proxies
          (if (env :ssl)
            (assoc secure-site-defaults :proxy (env :proxy))
            site-defaults)
          #_site-defaults
          (assoc-in [:security :anti-forgery] false)

          (dissoc :session)))
      wrap-context
      ;; wrap-reload-headers
      (wrap-mw-fn #'embedded-iframe)                        ;; Removes X-Frame-Options SAMEORIGIN from requests to embedded
      (wrap-mw-fn #'internal-error)
      (wrap-mw-fn #'request-state)
      (wrap-mw-fn #'debug-redefs)))