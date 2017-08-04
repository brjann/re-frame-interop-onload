(ns bass4.responses.auth
  (:require [bass4.services.auth :as auth-service]
            [bass4.services.user :as user]
            [bass4.services.assessments :as administrations]
            [ring.util.http-response :as response]
            [schema.core :as s]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [bass4.layout :as layout]
            [bass4.sms-sender :as sms]
            [bass4.mailer :as mail]))



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

(defn- send-code!
  [code user-sms user-email by-sms by-email allow-both]
  (let [methods-user    (send-methods-user user-sms user-email)
        methods-general (send-methods-general by-sms by-email allow-both)
        methods         {:sms   (and (:sms methods-user) (:sms methods-general))
                         :email (and (:email methods-user) (:email methods-general))}]
    (if (when (:sms methods)
          (sms/send-db-sms! user-sms code))
      true
      (when (:email methods)
        (mail/mail! user-email "code" code)))))

(defn- redirect-map
  [user]
  (if-let [settings (auth-service/double-auth-required? (:user-id user))]
    (let [code (auth-service/double-auth-code)]
      (if (send-code! code
                      (:sms-number user)
                      (:email user)
                      (:sms settings)
                      (:email settings)
                      (and (:double-auth-use-both user) (:allow-both settings)))
        {:redirect "/double-auth"
         :session  {:double-authed    nil
                    :double-auth-code code}}
        {:redirect "/double-auth-fail"}))
    {:redirect "/user/messages"}))


;; ------------
;;    LOGIN
;; ------------

(defn login-page []
  (layout/render
    "login.html"))

(defn- new-session-map
  [user-id]
  {:identity          user-id
   :auth-timeout      nil
   :last-request-time (t/now)
   :session-start     (t/now)})


(defn- assessments-map
  [user]
  (when (< 0 (administrations/create-assessment-round-entries! (:user-id user)))
    {:assessments-pending true}))

(s/defn ^:always-validate handle-login [session username :- s/Str password :- s/Str]
  (if-let [user (auth-service/authenticate-by-username username password)]
    (let [new-session (new-session-map (:user-id user))
          redirect    (redirect-map user)
          rounds      (assessments-map user)]
      (-> (response/found (:redirect redirect))
          (assoc :session (merge session new-session (:session redirect) rounds))))
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
  (if (:auth-timeout session)
    (re-auth-page return-url)
    (if (:identity session)
      (response/found "/user/")
      (response/found "/login"))))

(defn handle-re-auth
  [session password response]
  (if-let [user-id (:identity session)]
    (if (:auth-timeout session)
      (if (auth-service/authenticate-by-user-id user-id password)
        (-> response
            (assoc :session (merge session {:auth-timeout nil})))
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