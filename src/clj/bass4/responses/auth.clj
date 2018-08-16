(ns bass4.responses.auth
  (:require [bass4.services.auth :as auth-service]
            [bass4.services.user :as user-service]
            [bass4.services.assessments :as administrations]
            [ring.util.http-response :as http-response]
            [schema.core :as s]
            [bass4.i18n :as i18n]
            [clojure.tools.logging :as log]
            [bass4.config :refer [env]]
            [clj-time.core :as t]
            [bass4.layout :as layout]
            [bass4.sms-sender :as sms]
            [bass4.mailer :as mail]
            [bass4.i18n :as i18n]
            [bass4.db-config :as db-config]
            [bass4.api-coercion :as api :refer [def-api]]
            [bass4.services.bass :as bass-service]))


;; -------------------
;;    NO ACTIVITIES
;; -------------------

(def-api no-activities-page []
  (layout/render
    "auth/no-activities.html"))

;; -------------
;;    LOGOUT
;; -------------

(def-api logout []
  (-> (http-response/found "/login")
      (assoc :session {})))

;; ------------------
;;    DOUBLE-AUTH
;; ------------------


(defn not-authenticated? [session]
  (nil? (:identity session)))

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
    (not (auth-service/double-auth-required? (:identity session))) "/user/"
    (double-auth-done? session) "/user/"
    (double-auth-no-code session) "/login"))

(defn need-double-auth? [session]
  (cond
    (:external-login? session)
    false

    (:double-authed? session)
    false

    :else
    (auth-service/double-auth-required? (:identity session))))

(def-api double-auth
  [session :- api/?map?]
  (if-let [redirect (double-auth-redirect session)]
    (http-response/found redirect)
    (double-auth-page (:double-auth-code session))))

(def-api double-auth-check
  [session :- api/?map? code :- api/str+!]
  (if-let [redirect (double-auth-redirect session)]
    (http-response/found redirect)
    (if (= code (:double-auth-code session))
      (-> (http-response/found "/user/")
          (assoc :session (assoc session :double-authed? true :double-auth-code nil)))
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

(def-api login-page []
  (layout/render
    "auth/login.html"))

(defn create-new-session
  [user additional]
  (auth-service/register-user-login! (:user-id user))
  (merge
    {:identity          (:user-id user)
     :auth-re-auth      nil
     :last-login-time   (:last-login-time user)
     :last-request-time (t/now)
     :session-start     (t/now)}
    additional))

(def-api handle-login
  [username :- api/str+! password :- api/str+!]
  (if-let [user (auth-service/authenticate-by-username username password)]
    (let [{:keys [redirect error session]} (redirect-map user)]
      (if error
        (layout/error-422 error)
        (-> (http-response/found redirect)
            (assoc :session (create-new-session user session)))))
    (layout/error-422 "error")))


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

(def-api re-auth
  [session :- api/?map? return-url :- api/?str!]
  (if (:auth-re-auth session)
    (re-auth-page return-url)
    (if (:identity session)
      (http-response/found "/user/")
      (http-response/found "/login"))))

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
    (http-response/forbidden)))

;; TODO: Validate URL
;; [commons-validator "1.5.1"]
;; https://commons.apache.org/proper/commons-validator/apidocs/org/apache/commons/validator/routines/UrlValidator.html
(def-api check-re-auth
  [session :- api/?map? password :- api/str+! return-url :- api/?str!]
  (handle-re-auth session password
                  (http-response/found (if (nil? return-url)
                                         "/user/"
                                         return-url))))

(def-api check-re-auth-ajax
  [session :- api/?map? password :- api/str+!]
  (handle-re-auth session password (http-response/ok "ok")))


;; --------------------
;;  RE-AUTH MIDDLEWARE
;; --------------------


(defn re-auth-timeout
  []
  (or (env :timeout-soft) (* 30 60)))

(defn- request-string
  "Return the request part of the request."
  [request]
  (str (:uri request)
       (when-let [query (:query-string request)]
         (str "?" query))))

(defn- should-re-auth?
  [session now last-request-time re-auth-time-limit]
  (cond
    (:external-login? session) false
    (:auth-re-auth session) true
    (nil? last-request-time) nil
    (let [time-elapsed (t/in-seconds (t/interval last-request-time now))]
      (>= time-elapsed re-auth-time-limit)) true
    :else nil))

(defn auth-re-auth-wrapper
  [handler request]
  (let [session           (:session request)
        now               (t/now)
        last-request-time (:last-request-time session)
        re-auth?          (should-re-auth? session now last-request-time (re-auth-timeout))
        response          (if re-auth?
                            (if (= (:request-method request) :get)
                              (http-response/found (str "/re-auth?return-url=" (request-string request)))
                              (re-auth-440))
                            (handler (assoc-in request [:session :auth-re-auth] re-auth?)))
        session-map       {:last-request-time now
                           :auth-re-auth      (if (contains? (:session response) :auth-re-auth)
                                                (:auth-re-auth (:session response))
                                                re-auth?)}]

    (assoc response :session (if (nil? (:session response))
                               (merge session session-map)
                               (merge (:session response) session-map)))))


;; -----------------------
;;  RESTRICTED MIDDLEWARE
;; -----------------------

#_(defn wrap-restricted [handler request]
  (log/debug "Checking restricted")
  (if (:identity request)
    (handler request)
    (layout/error-403-page)))

(defn identity-mw [handler request]
  (if-let [id (get-in request [:session :identity])]
    (handler (assoc request :identity id))
    (handler request)))