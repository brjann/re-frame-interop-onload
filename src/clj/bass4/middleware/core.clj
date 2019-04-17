(ns bass4.middleware.core
  (:require [bass4.env :refer [defaults]]
            [clojure.tools.logging :as log]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [bass4.utils :refer [filter-map time+ nil-zero? fnil+]]
            [ring.middleware.session :as ring-session]
            [bass4.session.storage :as session-storage]
            [bass4.session.timeout :as session-timeout]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults secure-site-defaults]]
            [cprop.tools]
            [bass4.db.core :as db]
            [bass4.config :refer [env]]
            [bass4.request-state :as request-state]
            [bass4.middleware.debug :as debug-mw]
            [bass4.middleware.request-state :refer [request-state]]
            [bass4.middleware.response-transformation :as transform]
            [bass4.middleware.embedded :as embedded-mw]
            [bass4.middleware.errors :as errors-mw]
            [bass4.middleware.file-php :as file-php]
            [bass4.services.attack-detector :as a-d]
            [bass4.responses.auth :as auth]
            [bass4.responses.e-auth :as e-auth]
            [bass4.routes.ext-login :as ext-login]
            [bass4.responses.auth :as auth-response]
            [bass4.services.user :as user-service]
            [ring.util.http-response :as http-response]
            [bass4.config :as config]))


(defn wrap-formats [handler]
  (let [wrapped (wrap-restful-format
                  handler
                  {:formats [:json-kw :transit-json :transit-msgpack]})]
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



;; -----------------
;;    CSRF ERROR
;; -----------------


(defn- csrf-error
  ;; Takes request as argument but underscored to avoid unused variable type hint
  [_]
  (request-state/add-to-state-key! :info "CSRF error")
  (http-response/forbidden "Invalid anti-forgery token"))

(def ^:dynamic *skip-csrf* false)
(defn csrf-wrapper
  [handler request]
  (if (or *skip-csrf* (:csrf-disabled (:session request)))
    (handler request)
    ((wrap-anti-forgery
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
  (request-state/set-state! :session-cookie (get-in request [:cookies "JSESSIONID" :value]
                                                    (get-in request [:cookies "ring-session" :value])))
  (let [assessments-pending-pre?  (get-in request [:session :assessments-pending?])
        res                       (handler (if-let [user (user-service/get-user (:user-id request))]
                                             (assoc-in request [:db :user] user)
                                             (merge request {:user-id nil :session (dissoc (:session request) :user-id)})))
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

;; TODO: Maybe move into session ns
(defn request-state-session-info
  [handler request]
  (let [session (:session request)]
    (request-state/set-state! :user-id (:user-id session))
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
      wrap-formats                                          ; This used to be in def-routes.
      (wrap-mw-fn #'errors-mw/wrap-api-error)
      (wrap-mw-fn #'auth-response/privacy-notice-error-mw)
      (wrap-mw-fn #'ext-login/return-url-mw)
      (wrap-mw-fn #'e-auth/bankid-middleware)
      (wrap-mw-fn #'request-db-user-mw)
      debug-mw/wrap-prone-debug-exceptions
      (wrap-mw-fn #'file-php/File-php)
      (wrap-mw-fn #'db/db-middleware)
      (wrap-mw-fn #'a-d/attack-detector-mw)
      (wrap-mw-fn #'auth/session-user-id-mw)
      (wrap-mw-fn #'request-state-session-info)
      (wrap-mw-fn #'transform/transform-mw)
      debug-mw/wrap-session-modification
      (session-timeout/wrap-session-hard-timeout)
      (wrap-mw-fn #'embedded-mw/wrap-embedded-request)      ; Must be before timeout handler to override hard-timeout
      (ring-session/wrap-session
        {:cookie-attrs {:http-only true}
         :store        (session-storage/jdbc-store #'db/db-common)})
      wrap-reload-headers
      wrap-webjars
      (wrap-defaults
        (->
          ;; TODO: Remove (env :ssl) if everything seems to be working
          ;; https://github.com/ring-clojure/ring-defaults#proxies
          #_(if (env :ssl)
            (assoc secure-site-defaults :proxy (env :proxy))
            site-defaults)
          site-defaults
          (assoc-in [:security :anti-forgery] false)
          (dissoc :session)))
      (wrap-mw-fn #'embedded-mw/embedded-iframe)            ;; Removes X-Frame-Options SAMEORIGIN from requests to embedded
      (wrap-mw-fn #'errors-mw/catch-internal-error-mw)
      (wrap-mw-fn #'request-state)))