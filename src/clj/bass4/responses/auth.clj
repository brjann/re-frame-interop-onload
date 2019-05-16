(ns bass4.responses.auth
  (:require [bass4.services.auth :as auth-service]
            [bass4.http-errors :as http-errors]
            [ring.util.http-response :as http-response]
            [schema.core :as s]
            [bass4.i18n :as i18n]
            [bass4.config :refer [env]]
            [clj-time.core :as t]
            [bass4.layout :as layout]
            [bass4.sms-sender :as sms]
            [bass4.email :as mail]
            [bass4.i18n :as i18n]
            [bass4.db-config :as db-config]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.services.bass :as bass-service]
            [bass4.services.privacy :as privacy-service]
            [bass4.session.timeout :as session-timeout]
            [bass4.session.create :as session-create])
  (:import (clojure.lang ExceptionInfo)))


;; -------------------
;;    NO ACTIVITIES
;; -------------------

(defapi no-activities-page []
  (layout/render
    "auth/no-activities.html"
    {:in-session? false}))

;; -------------
;;    LOGOUT
;; -------------

(defapi logout [session]
  (-> (http-response/found (or (:logout-path session) "/login"))
      (assoc :session nil)))

;; ------------------
;;    DOUBLE-AUTH
;; ------------------


(defn not-authenticated? [session]
  (nil? (:user-id session)))

(defn double-auth-no-code [session]
  (nil? (:double-auth-code session)))

(defn double-auth-done? [session]
  (:double-authed? session))

(defn- double-auth-page [double-auth-code]
  (layout/render
    "auth/double-auth.html"
    (when (db-config/debug-mode?)
      {:double-auth-code double-auth-code})))

(defn- double-auth-redirect [session]
  (cond
    (not-authenticated? session) "/login"
    (not (auth-service/double-auth-required? (:user-id session))) "/user/"
    (double-auth-done? session) "/user/"
    (double-auth-no-code session) "/login"))

(defn need-double-auth? [session]
  (cond
    (:external-login? session)
    false

    (:double-authed? session)
    false

    :else
    (auth-service/double-auth-required? (:user-id session))))

(defapi double-auth
  [session :- [:? map?]]
  (if-let [redirect (double-auth-redirect session)]
    (http-response/found redirect)
    (double-auth-page (:double-auth-code session))))

(defapi double-auth-check
  [session :- [:? map?] code :- [[api/str? 1 20]]]
  (if-let [redirect (double-auth-redirect session)]
    (http-response/found redirect)
    (if (= code (:double-auth-code session))
      (-> (http-response/found "/user/")
          (assoc :session (assoc session :double-authed? true :double-auth-code nil)))
      (http-errors/error-422 "error"))))


;; --------------------------
;;    LOGIN -> DOUBLE AUTH
;; --------------------------


(defn- send-methods-user
  [user-sms user-email]
  {:sms   (not (empty? user-sms))
   :email (mail/is-email? user-email)})

(defn- send-methods-general
  [by-sms? by-email? allow-both?]
  {:sms   (or by-sms? allow-both?)
   :email (or by-email? allow-both?)})

(defn- get-send-methods
  [user-sms user-email by-sms? by-email? allow-both?]
  (let [methods-user    (send-methods-user user-sms user-email)
        methods-general (send-methods-general by-sms? by-email? allow-both?)]
    {:sms   (and (:sms methods-user) (:sms methods-general))
     :email (and (:email methods-user) (:email methods-general))}))

(defn- send-by-method!
  [code send-methods user-sms user-email]
  (cond
    (:sms send-methods)
    (sms/queue-sms! user-sms code)

    (:email send-methods)
    (mail/queue-email! user-email (i18n/tr [:login/code]) code)

    :else
    (throw (Exception. "No sending method"))))

(defn- send-code!
  [code user-sms user-email by-sms? by-email? allow-both?]
  (let [send-methods (get-send-methods user-sms user-email by-sms? by-email? allow-both?)]
    (if (some identity (vals send-methods))
      (do (send-by-method! code send-methods user-sms user-email)
          :success)
      :no-method)))

;; TODO: This is not perfect - wrong password and then no-method looks weird
(defn- no-method-message
  [user]
  (let [email (bass-service/db-contact-info user)]
    (str "message " (i18n/tr [:login/no-send-method] [(:email email)]))))

(defn- redirect-map
  [user]
  (if-let [settings (auth-service/double-auth-required? (:user-id user))]
    (let [code     (auth-service/double-auth-code)
          send-res (send-code! code
                               (:sms-number user)
                               (:email user)
                               (:sms? settings)
                               (:email? settings)
                               (and (:double-auth-use-both? user) (:allow-both? settings)))]
      (case send-res
        :success
        {:redirect "/double-auth"
         :session  {:double-authed?   nil
                    :double-auth-code code}}

        :no-method
        {:error (no-method-message user)}

        :send-error
        {:redirect "/user/"
         :session  {:double-authed? true}}))

    {:redirect "/user/"}))


;; ------------
;;    LOGIN
;; ------------

(defapi login-page []
  (layout/render
    "auth/login.html"
    {:in-session? false}))

(defn create-new-session
  [user additional]
  (when (not (:project-id user))
    (throw (ex-info "Incomplete user map submitted to create session" user)))
  (auth-service/register-user-login! (:user-id user))
  (when (and (not (privacy-service/privacy-notice-disabled?))
             (not (privacy-service/privacy-notice-exists? (:project-id user))))
    (throw (ex-info "No privacy notice" {:type ::no-privacy-notice})))
  (session-create/new user additional))

(defapi handle-login
  [username :- [[api/str? 1 100]] password :- [[api/str? 1 100]]]
  (if-let [user (auth-service/authenticate-by-username username password)]
    (let [{:keys [redirect error session]} (redirect-map user)]
      (if error
        (http-errors/error-422 error)
        (-> (http-response/found redirect)
            (assoc :session (create-new-session user session)))))
    (http-errors/error-422 "error")))


;; -------------
;;    RE-AUTH
;; -------------

(defn- re-auth-page
  ([return-url] (re-auth-page return-url false))
  ([return-url error] (layout/render
                        "auth/re-auth.html"
                        {:return-url return-url
                         :error      error})))

(defapi re-auth
  [session :- [:? map?] return-url :- [:? [api/str? 1 2000]]]
  (if (:auth-re-auth? session)
    (re-auth-page return-url)
    (if (:user-id session)
      (http-response/found "/user/")
      (http-response/found "/login"))))

(defn handle-re-auth
  [session password response]
  (if-let [user-id (:user-id session)]
    (if (:auth-re-auth? session)
      (if (auth-service/authenticate-by-user-id user-id password)
        (-> response
            (assoc :session (session-timeout/reset-re-auth session)))
        (http-errors/error-422 "error"))
      response)
    (http-response/forbidden)))

;; TODO: Validate URL
;; [commons-validator "1.5.1"]
;; https://commons.apache.org/proper/commons-validator/apidocs/org/apache/commons/validator/routines/UrlValidator.html
(defapi check-re-auth
  [session :- [:? map?] password :- [[api/str? 1 100]] return-url :- [:? [api/str? 1 2000]]]
  (handle-re-auth session password
                  (http-response/found (if (nil? return-url)
                                         "/user/"
                                         return-url))))

(defapi check-re-auth-ajax
  [session :- [:? map?] password :- [[api/str? 1 100]]]
  (handle-re-auth session password (http-response/ok "ok")))

(defapi check-re-auth-api
  [session :- [:? map?] password :- [[api/str? 1 100]]]
  (handle-re-auth session password (http-response/ok {:result "ok"})))



;; --------------------
;;    ESCALATE USER
;; --------------------

(defapi escalate-login-page
  []
  (layout/render "auth/escalate.html"))

(defn handle-escalation*
  [session password]
  (let [user-id (:user-id session)]
    (cond
      (nil? user-id)
      (http-response/forbidden)

      (not (:limited-access? session))
      (http-response/found "/user")

      (auth-service/authenticate-by-user-id user-id password)
      (-> (http-response/found "/user")
          (assoc :session (merge session {:external-login?   nil
                                          :limited-access?   nil
                                          :double-authed?    true})))

      :else
      (http-errors/error-422 "error"))))

(defapi handle-escalation
  [session :- [:? map?] password :- [[api/str? 1 100]]]
  (handle-escalation* session password))


(defn double-auth-mw
  [handler]
  (fn [request]
    (if (need-double-auth? (:session request))
      (http-response/found "/double-auth")
      (handler request))))

;; -----------------------
;;  RESTRICTED MIDDLEWARE
;; -----------------------

(defn restricted-mw [handler]
  (fn [request]
    (if (:user-id request)
      (handler request)
      (http-response/forbidden))))


(defn session-user-id-mw [handler request]
  (if-let [id (get-in request [:session :user-id])]
    (handler (assoc request :user-id id))
    (handler request)))

;; ---------------------------
;;  PRIVACY NOTICE MIDDLEWARE
;; ---------------------------

(defn privacy-notice-error-mw [handler request]
  (try
    (handler request)
    (catch ExceptionInfo e
      (if (= (:type (.data e)) ::no-privacy-notice)
        (http-response/found "/missing-privacy-notice")
        (throw e)))))