(ns bass4.responses.auth
  (:require [bass4.services.auth :as auth-service]
            [bass4.services.user :as user-service]
            [bass4.services.assessments :as administrations]
            [ring.util.http-response :as response]
            [schema.core :as s]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [bass4.layout :as layout]
            [bass4.sms-sender :as sms]
            [bass4.mailer :as mail]
            [bass4.i18n :as i18n]))



(defn error-422
  "https://en.wikipedia.org/wiki/List_of_HTTP_status_codes
  422 Unprocessable Entity (WebDAV; RFC 4918)
  The request was well-formed but was unable to be followed due to semantic errors.

  Used to communicate back to form that there was something wrong with
  the posted data. For example erroneous username-password combination"
  ([] (error-422 ""))
  ([body]
   {:status  422
    :headers {}
    :body    body}))


;; -------------
;;    LOGOUT
;; -------------

(defn logout []
  (-> (response/found "/login")
      (assoc :session nil)))




;; ------------------
;;    DOUBLE-AUTH
;; ------------------

(defn- double-auth-page [double-auth-code]
  (layout/render
    "double-auth.html"
    {:double-auth-code double-auth-code}))

(defn- double-auth-redirect [session]
  (cond
    (auth-service/not-authenticated? session) "/login"
    (not (auth-service/double-auth-required? (:identity session))) "/user/"
    (auth-service/double-auth-done? session) "/user/"
    (auth-service/double-auth-no-code session) "/login"))

(defn double-authed? [session]
  (if (auth-service/double-auth-required? (:identity session))
    (boolean (:double-authed session))
    true))

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
      (error-422 "error"))))


;; --------------------------
;;    LOGIN -> DOUBLE AUTH
;; --------------------------


(defn- send-methods-user
  [user-sms user-email]
  {:sms   (not (empty? user-sms))
   :email (mail/is-email? user-email)})

(defn- send-methods-general
  [by-sms by-email allow-both]
  {:sms   (or (pos? by-sms) (pos? allow-both))
   :email (or (pos? by-email) (pos? allow-both))})

(defn- get-send-methods
  [user-sms user-email by-sms by-email allow-both]
  (let [methods-user    (send-methods-user user-sms user-email)
        methods-general (send-methods-general by-sms by-email allow-both)]
    {:sms   (and (:sms methods-user) (:sms methods-general))
     :email (and (:email methods-user) (:email methods-general))}))

(defn- send-by-method!
  [code send-methods user-sms user-email]
  (if (when (:sms send-methods)
        (sms/send-db-sms! user-sms code))
    true
    (when (:email send-methods)
      (mail/mail! user-email "code" code))))

(defn- send-code!
  [code user-sms user-email by-sms by-email allow-both]
  (let [send-methods (get-send-methods user-sms user-email by-sms by-email allow-both)]
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
                               (:sms settings)
                               (:email settings)
                               (and (:double-auth-use-both user) (:allow-both settings)))]
      (case send-res
        :success {:redirect "/double-auth"
                  :session  {:double-authed    nil
                             :double-auth-code code}}
        :no-method {:error (no-method-message user)}
        :send-error {:redirect "/user/"
                     :session  {:double-authed true}}))
    {:redirect "/user/"}))


;; ------------
;;    LOGIN
;; ------------

(defn login-page []
  (layout/render
    "login.html"))

;; TODO: Should the new session contain for example :admin nil?
;; See question below.
(defn- new-session-map
  [user-id]
  {:identity          user-id
   :auth-re-auth      nil
   :last-request-time (t/now)
   :session-start     (t/now)})


(defn- assessments-map
  [user]
  (when (< 0 (administrations/create-assessment-round-entries! (:user-id user)))
    {:assessments-pending true}))

;; TODO: Does the new session map really have to be merged with the old one?
;; Doesn't that risk that previous sessions "leak" into the new one? For example
;; if new user logs in without logging out out.
(defn- login-response
  [user redirect session]
  (let [new-session (new-session-map (:user-id user))
        rounds (assessments-map user)]
    (-> (response/found (:redirect redirect))
        (assoc :session (merge #_session new-session (:session redirect) rounds)))))

(s/defn ^:always-validate handle-login [session username :- s/Str password :- s/Str]
  (if-let [user (auth-service/authenticate-by-username username password)]
    (let [redirect (redirect-map user)]
      (if (:error redirect)
        (error-422 (:error redirect))
        (login-response user redirect session)))
    (error-422 "error")))



;; -------------
;;    RE-AUTH
;; -------------

(defn- re-auth-page
  ([return-url] (re-auth-page return-url false))
  ([return-url error] (layout/render
                        "re-auth.html"
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
        (error-422 "error"))
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