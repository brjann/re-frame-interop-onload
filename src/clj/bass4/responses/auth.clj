(ns bass4.responses.auth
  (:require [bass4.services.auth :as auth-service]
            [bass4.services.user :as user-service]
            [bass4.services.assessments :as administrations]
            [ring.util.http-response :as response]
            [schema.core :as s]
            [bass4.i18n :as i18n]
            [clojure.tools.logging :as log]
            [bass4.config :refer [env]]
            [clj-time.core :as t]
            [bass4.layout :as layout]
            [bass4.sms-sender :as sms]
            [bass4.mailer :as mail]
            [bass4.i18n :as i18n]
            [bass4.services.attack-detector :as a-d]))


;; -------------------
;;    NO ACTIVITIES
;; -------------------

(defn no-activities-page []
  (layout/render
    "auth/no-activities.html"))

;; -------------
;;    LOGOUT
;; -------------

(defn logout []
  (-> (response/found "/login")
      (assoc :session {})))

;; ------------------
;;    DOUBLE-AUTH
;; ------------------

(defn- double-auth-page [double-auth-code]
  (layout/render
    "auth/double-auth.html"
    (when (or (env :dev) (env :debug-mode)) {:double-auth-code double-auth-code})))

(defn- double-auth-redirect [session]
  (cond
    (auth-service/not-authenticated? session) "/login"
    (not (auth-service/double-auth-required? (:identity session))) "/user/"
    (auth-service/double-auth-done? session) "/user/"
    (auth-service/double-auth-no-code session) "/login"))

(defn need-double-auth? [session]
  (cond
    (:external-login session)
    false

    ;; TODO: Check :double-authed first!
    (auth-service/double-auth-required? (:identity session))
    (not (boolean (:double-authed session)))

    :else
    false))

(defn double-auth [session]
  (if-let [redirect (double-auth-redirect session)]
    (response/found redirect)
    (double-auth-page (:double-auth-code session))))

(s/defn ^:always-validate double-auth-check [session code :- s/Str]
  (if-let [redirect (double-auth-redirect session)]
    (response/found redirect)
    (if (= code (:double-auth-code session))
      (-> (response/found "/user/")
          (assoc :session (assoc session :double-authed 1 :double-auth-code nil)))
      (layout/error-422 "error"))))


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
  (if (when (:sms send-methods)
        (sms/send-db-sms! user-sms code))
    true
    (when (:email send-methods)
      (mail/mail! user-email (i18n/tr [:login/code]) code))))

(defn- send-code!
  [code user-sms user-email by-sms? by-email? allow-both?]
  (let [send-methods (get-send-methods user-sms user-email by-sms? by-email? allow-both?)]
    (if (some identity (vals send-methods))
      (if (send-by-method! code send-methods user-sms user-email)
        :success
        :send-error)
      :no-method)))


;; TODO: What if no email?
;; TODO: This is not perfect - wrong password and then no-method looks weird
(defn- no-method-message
  [user]
  (let [email (user-service/support-email user)]
    (str "message " (i18n/tr [:login/no-send-method] [email]))))

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
         :session  {:double-authed    nil
                    :double-auth-code code}}

        :no-method
        {:error (no-method-message user)}

        :send-error
        {:redirect "/user/"
         :session  {:double-authed true}}))

    {:redirect "/user/"}))


;; ------------
;;    LOGIN
;; ------------

(defn login-page []
  (layout/render
    "auth/login.html"))


(defn- assessments-map
  [user]
  (when (< 0 (administrations/create-assessment-round-entries! (:user-id user)))
    {:assessments-pending?   true
     :assessments-performed? true}))

(defn create-new-session
  [user additional check-assessments?]
  (auth-service/register-user-login! (:user-id user))
  (merge
    {:identity          (:user-id user)
     :auth-re-auth      nil
     :last-login-time   (:last-login-time user)
     :last-request-time (t/now)
     :session-start     (t/now)}
    additional
    (when check-assessments?
      (assessments-map user))))

(s/defn
  ^:always-validate
  handle-login
  [request username :- s/Str password :- s/Str]
  (a-d/delay-if-blocked! request)
  (if-let [user (auth-service/authenticate-by-username username password)]
    (let [{:keys [redirect error session]} (redirect-map user)]
      (a-d/register-successful-login! request)
      (if error
        (layout/error-422 error)
        (-> (response/found redirect)
            (assoc :session (create-new-session user session true)))))
    (do
      (a-d/register-failed-login! :login request {:username username})
      (layout/error-422 "error"))))


;; -------------
;;    RE-AUTH
;; -------------

(defn- re-auth-page
  ([return-url] (re-auth-page return-url false))
  ([return-url error] (layout/render
                        "auth/re-auth.html"
                        {:return-url return-url
                         :error      error})))
(defn re-auth-440
  ([] (re-auth-440 ""))
  ([body]
   {:status  440
    :headers {}
    :body    body}))

(defn re-auth [session return-url]
  (if (:auth-re-auth session)
    (re-auth-page return-url)
    (if (:identity session)
      (response/found "/user/")
      (response/found "/login"))))

(defn handle-re-auth
  [session password response]
  (if-let [user-id (:identity session)]
    (if (:auth-re-auth session)
      (if (auth-service/authenticate-by-user-id user-id password)
        (-> response
            (assoc :session (merge session {:auth-re-auth      nil
                                            :last-request-time (t/now)})))
        (layout/error-422 "error"))
      response)
    (response/forbidden)))

;; TODO: Validate URL
;; [commons-validator "1.5.1"]
;; https://commons.apache.org/proper/commons-validator/apidocs/org/apache/commons/validator/routines/UrlValidator.html
(s/defn ^:always-validate check-re-auth
  [session password :- s/Str return-url]
  (handle-re-auth session password
                  (response/found (if (nil? return-url)
                                    "/user/"
                                    return-url))))

(s/defn check-re-auth-ajax [session password :- s/Str]
  (handle-re-auth session password (response/ok "ok")))