(ns bass4.middleware.core
  (:require [compojure.core :refer [defroutes context GET POST ANY routes]]
            [bass4.env :refer [defaults]]
            [clojure.tools.logging :as log]
            [bass4.layout :refer [*app-context* error-page error-400-page]]
            [bass4.services.bass :as bass]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [bass4.bass-locals :as bass-locals]
            [bass4.utils :refer [filter-map time+ nil-zero?]]
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
            [clj-time.coerce :as tc]
            [bass4.layout :as layout]
            [bass4.services.user :as user]
            [clojure.string :as string]
            [bass4.middleware.debug :refer [debug-redefs wrap-debug-exceptions wrap-session-modification]]
            [bass4.middleware.request-state :refer [request-state]]
            [bass4.middleware.ajax-post :refer [ajax-post]]
            [bass4.middleware.embedded :refer [embedded]]
            [bass4.middleware.errors :refer [internal-error]]
            [ring.util.http-response :as response]
            [bass4.responses.auth :as auth-response])
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
          :title  "Invalid anti-forgery token"})}))

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

;; TODO: http://stackoverflow.com/questions/8861181/clear-all-fields-in-a-form-upon-going-back-with-browser-back-button
(defn wrap-reload-headers [handler]
  (fn [request]
    (let [response (handler request)]
      (update-in response
                 [:headers] #(assoc %1
                               "Cache-Control" "no-cache, no-store, must-revalidate"
                               "Expires" "Wed, 12 Jul 1978 08:00:00 GMT"
                               "Pragma" "no-cache")))))


;; -----------------
;;  RE-AUTHENTICATE
;; -----------------


(defn request-string
  "Return the request part of the request."
  [request]
  (str (:uri request)
       (when-let [query (:query-string request)]
         (str "?" query))))

(defn auth-re-auth-wrapper
  [handler request]
  (let [session            (:session request)
        now                (t/now)
        last-request-time  (:last-request-time session)
        re-auth-time-limit (or (env :timeout-soft) (* 30 60))
        re-auth?           (cond
                             (:auth-re-auth session) true
                             (nil? last-request-time) nil
                             (let [time-elapsed (t/in-seconds (t/interval last-request-time now))]
                               (> time-elapsed re-auth-time-limit)) true
                             :else nil)
        response           (if re-auth?
                             (if (= (:request-method request) :get)
                               (response/found (str "/re-auth?return-url=" (request-string request)))
                               (auth-response/re-auth-440))
                             (handler (assoc-in request [:session :auth-re-auth] re-auth?)))
        session-map        {:last-request-time now
                            :auth-re-auth      (if (contains? (:session response) :auth-re-auth)
                                                 (:auth-re-auth (:session response))
                                                 re-auth?)}]

    (assoc response :session (if (nil? (:session response))
                               (merge session session-map)
                               (merge (:session response) session-map)))))


(defn user-identity
  [handler request]
  "Check if user in identity exists
    yes: add user map to session
    no: remove :identity key from from session"
  (handler (if-let [user (user/get-user (:identity request))]
             (assoc-in request [:session :user] user)
             (merge request {:identity nil :session (dissoc (:session request) :identity)}))))

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
(defn session-state
  [handler request]
  (let [session (:session request)]
    (request-state/set-state! :user-id (:identity session))
    (request-state/set-state! :session-start (:session-start session)))
  (handler request))


(defn File-php
  [handler request]
  (let [uri             (:uri request)
          length        (count uri)
          uploaded-file (and
                          (< 8 length)
                          (= "File.php" (subs uri (- length 8)))
                          (get-in request [:params :uploadedfile]))]
    (log/debug (and
                 (< 8 length)
                 (= "File.php" (subs uri (- length 8)))
                 (get-in request [:params :uploadedfile])))
    (if uploaded-file
      (layout/text-response uploaded-file)
      (handler request))))

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
      (wrap-mw-fn #'user-identity)
      wrap-debug-exceptions
      (wrap-mw-fn #'embedded)
      (wrap-mw-fn #'File-php)
      (wrap-mw-fn #'db/db-middleware)                       ;; wrap-db
      (wrap-mw-fn #'ajax-post)
      wrap-auth
      wrap-reload-headers
      ;;File-php-wrapper
      wrap-webjars
      wrap-flash
      (wrap-mw-fn #'session-state)                          ;;      wrap-session-state
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

          ;; DID NOT WORK OUT BECAUSE OF THIRD PARTY COOKIES
          ;; Remove frame-options and instead add on route-basis, to allow embedding.
          ;; (update-in [:security] dissoc :frame-options)

          (dissoc :session)))
      wrap-context
      (wrap-mw-fn #'internal-error)
      (wrap-mw-fn #'request-state)
      (wrap-mw-fn #'debug-redefs)))