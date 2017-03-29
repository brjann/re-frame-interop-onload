(ns bass4.responses.auth
  (:require [bass4.services.auth :as auth-service]
            [bass4.views.auth :as auth-view]
            [bass4.services.user :as user]
            [ring.util.http-response :as response]
            [schema.core :as s]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]))


(defn error-422
  ([] (error-422 ""))
  ([body]
   {:status 422
    :headers {}
    :body body}))


;; -------------
;;    LOGOUT
;; -------------

(defn logout []
  (-> (response/found "/login")
      (assoc :session nil)))

;; ------------
;;    LOGIN
;; ------------

(defn- new-session-map [id add-double-auth]
  (merge
    {:identity          id
     :auth-timeout      nil
     :last-request-time (t/now)}
    (when add-double-auth
      {:double-authed    nil
       :double-auth-code (auth-service/double-auth-code)})))

(s/defn ^:always-validate handle-login [session username :- s/Str password :- s/Str]
  (if-let [id (auth-service/authenticate-by-username username password)]
    (if (auth-service/double-auth-required?)
      (-> (response/found "/double-auth")
          (assoc :session
                 (merge session
                        (new-session-map id true))))
      (-> (response/found "/user/messages")
          (assoc :session (merge session (new-session-map id false)))))
    (error-422 "error")))



;; ------------------
;;    DOUBLE-AUTH
;; ------------------

(defn- double-auth-redirect [session]
  (cond
    (auth-service/not-authenticated? session) "/login"
    (auth-service/double-auth-done? session) "/user/"
    ;should this be more complex? double auth may be broken
    (auth-service/not-double-auth-ok? session) "/login"))

(defn double-authed? [session]
  (if (auth-service/double-auth-required?)
    (boolean (:double-authed session))
    false))

(defn double-auth [session]
  (if-let [redirect (double-auth-redirect session)]
    (response/found redirect)
    (auth-view/double-auth (:double-auth-code session))))

(s/defn ^:always-validate double-auth-check [session code :- s/Str]
  (if-let [redirect (double-auth-redirect session)]
    (response/found redirect)
    (if (= code (:double-auth-code session))
      (-> (response/found "/user/")
          (assoc :session (assoc session :double-authed 1 :double-auth-code nil)))
      (error-422 "error"))))



;; -------------
;;    RE-AUTH
;; -------------

(defn re-auth-440
  ([] (re-auth-440 ""))
  ([body]
   {:status 440
    :headers {}
    :body body}))

(defn re-auth [session return-url]
  (if (:auth-timeout session)
    (auth-view/re-auth-page return-url)
    (if (:identity session)
      (response/found "/user/")
      (response/found "/login"))))

;; TODO: Validate URL
;; [commons-validator "1.5.1"]
;; https://commons.apache.org/proper/commons-validator/apidocs/org/apache/commons/validator/routines/UrlValidator.html
(s/defn ^:always-validate check-re-auth [session password :- s/Str return-url]
  (when (:auth-timeout session)
    (when-let [user-id (:identity session)]
      (if (auth-service/authenticate-by-user-id user-id password)
        (-> (response/found (if (nil? return-url)
                              "/user/"
                              return-url))
            (assoc :session (merge session {:auth-timeout nil})))
        (error-422 "error")))))

(s/defn check-re-auth-ajax [session password :- s/Str]
  (when (:auth-timeout session)
    (when-let [user-id (:identity session)]
      (if (auth-service/authenticate-by-user-id user-id password)
        (-> (response/ok "ok")
            (assoc :session (merge session {:auth-timeout nil})))
        #_(re-auth-440)
        (error-422 "error")))))