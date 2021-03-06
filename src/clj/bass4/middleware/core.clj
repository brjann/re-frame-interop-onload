(ns bass4.middleware.core
  (:require [bass4.env :refer [defaults]]
            [ring.middleware.webjars :as webjars]
            [ring.middleware.format :as format]
            [ring.util.http-response :as http-response]
            [bass4.session.storage :as session-storage]
            [bass4.session.timeout :as session-timeout]
            [ring.middleware.defaults :as defaults]
            [bass4.db.middleware :as db-middleware]
            [bass4.middleware.emoji-remover :as emojis]
            [bass4.middleware.debug :as debug-mw]
            [bass4.middleware.request-logger :as request-logger]
            [bass4.middleware.response-transformation :as transform]
            [bass4.embedded.middleware :as embedded-mw]
            [bass4.middleware.errors :as errors-mw]
            [bass4.middleware.file-php :as file-php]
            [bass4.services.attack-detector :as a-d]
            [bass4.responses.auth :as auth]
            [bass4.responses.e-auth :as e-auth]
            [bass4.routes.ext-login :as ext-login]
            [bass4.responses.auth :as auth-response]
            [bass4.services.user :as user-service]
            [bass4.config :as config]
            [bass4.middleware.lockdown :as lockdown]
            [ring.middleware.anti-forgery :as anti-forgery])
  (:import (com.fasterxml.jackson.core.io JsonEOFException)))

(defn json-request-error-handler*
  [e _ _]
  (if (= (class e) JsonEOFException)
    (http-response/bad-request "Bad JSON")
    (throw e)))

(defn json-request-error-handler
  [e handler req]
  (json-request-error-handler* e handler req))

(defn wrap-formats [handler]
  (let [wrapped (format/wrap-restful-format
                  handler
                  {:formats               [:json-kw :transit-json :transit-msgpack]
                   :request-error-handler json-request-error-handler})]
    (fn [request]
      ;; disable wrap-formats for websockets
      ;; since they're not compatible with this middleware
      ((if (:websocket? request) handler wrapped) request))))




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

(defn security-headers-mw
  [handler request]
  (let [response    (handler request)
        default-csp {"default-src" #{"'self'"}
                     "script-src"  #{"'unsafe-inline'" "'unsafe-eval'"}
                     "style-src"   #{"'unsafe-inline'"}
                     "img-src"     #{"*" "data:"}}
        config-csp  (config/env :content-security-policy)
        csp         (merge-with into default-csp config-csp)
        csp-string  (apply str
                           (map (fn [[k v]]
                                  (str k " "
                                       (apply str (interpose " " (conj v "'self'")))
                                       ";"))
                                csp))]
    (update-in response
               [:headers] #(merge %1
                                  {"Content-Security-Policy"   csp-string
                                   "Server"                    ""
                                   "Strict-Transport-Security" "max-age=7776000; includeSubDomains"}))))



;; -----------------
;;    CSRF ERROR
;; -----------------


(defn- csrf-error
  ;; Takes request as argument but underscored to avoid unused variable type hint
  [_]
  (request-logger/add-to-state-key! :info "CSRF error")
  (http-response/forbidden "Invalid anti-forgery token"))

(def ^:dynamic *skip-csrf* false)
(defn csrf-wrapper
  [handler request]
  (if (or *skip-csrf* (:csrf-disabled (:session request)))
    (handler request)
    ((anti-forgery/wrap-anti-forgery
       handler
       {:error-handler csrf-error}) request)))

(defn wrap-csrf
  [handler]
  (fn [request]
    (csrf-wrapper handler request)))


(defn request-db-user-mw
  [handler request]
  "Check if user in identity exists
    yes: add user map to session
    no: remove :user-id key from from session
    Also adds some request state info"
  (request-logger/set-state! :session-cookie (get-in request [:cookies "JSESSIONID" :value]
                                                     (get-in request [:cookies "ring-session" :value])))
  (let [assessments-pending-pre?  (get-in request [:session :assessments-pending?])
        res                       (handler (if-let [user (user-service/get-user (:user-id request))]
                                             (assoc-in request [:db :user] user)
                                             (merge request {:user-id nil :session (dissoc (:session request) :user-id)})))
        assessments-pending-post? (when (contains? res :session)
                                    (true? (get-in res [:session :assessments-pending?])))]
    (cond
      assessments-pending-post?
      (request-logger/add-to-state-key! :info "Assessments pending")

      (and assessments-pending-pre? (false? assessments-pending-post?))
      (request-logger/add-to-state-key! :info "Assessments completed")

      assessments-pending-pre?
      (request-logger/add-to-state-key! :info "Assessments pending"))
    res))

;; TODO: Maybe move into session ns
(defn request-state-session-info
  [handler request]
  (let [session (:session request)]
    (request-logger/set-state! :user-id (:user-id session))
    (request-logger/set-state! :session-start (:session-start session)))
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
      (wrap-mw-fn #'emojis/remove-emojis-mw)
      wrap-formats                                          ; This used to be in def-routes.
      (wrap-mw-fn #'errors-mw/wrap-api-error)
      (wrap-mw-fn #'auth-response/privacy-notice-error-mw)
      (wrap-mw-fn #'ext-login/return-url-mw)
      (wrap-mw-fn #'e-auth/bankid-middleware)
      (wrap-mw-fn #'request-db-user-mw)
      debug-mw/wrap-prone-debug-exceptions                  ; GOTCHA: Catches exceptions in dev mode and present the stack trace
      (wrap-mw-fn #'request-state-session-info)
      (wrap-mw-fn #'auth/session-user-id-mw)
      debug-mw/wrap-session-modification
      (wrap-mw-fn #'transform/transform-mw)                 ; This is a mess. See ns for info
      (session-timeout/wrap-session-hard-timeout)
      (wrap-mw-fn #'embedded-mw/wrap-embedded-request)      ; Must be before timeout handler to override hard-timeout
      (wrap-mw-fn #'file-php/File-php)                      ; Must be directly after db resolve so path restrictions (e.g., /embedded) are not applied
      (wrap-mw-fn #'a-d/attack-detector-mw)
      (session-storage/wrap-db-session)
      (wrap-mw-fn #'lockdown/sms-lockdown-mw)
      (wrap-mw-fn #'db-middleware/db-middleware)
      wrap-reload-headers
      (wrap-mw-fn #'lockdown/lockdown-mw)
      (wrap-mw-fn #'security-headers-mw)
      webjars/wrap-webjars
      (defaults/wrap-defaults
        (-> defaults/site-defaults
            (assoc-in [:security :anti-forgery] false)
            (dissoc :session)))
      (wrap-mw-fn #'embedded-mw/embedded-iframe)            ;; Removes X-Frame-Options SAMEORIGIN from requests to embedded
      (wrap-mw-fn #'errors-mw/catch-internal-error-mw)
      (wrap-mw-fn #'request-logger/wrap-logger)))