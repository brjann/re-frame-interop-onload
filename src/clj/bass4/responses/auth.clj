(ns bass4.responses.auth
  (:require [bass4.services.auth :as auth-service]
            [bass4.views.auth :as auth-view]
            [bass4.services.user :as user]
            [ring.util.http-response :as response]
            [schema.core :as s]))

(defn- double-auth-redirect [session]
  (cond
    (auth-service/not-authenticated? session) "/login"
    (auth-service/double-auth-done? session) "/user/"
    ;should this be more complex? double auth may be broken
    (auth-service/not-double-auth-ok? session) "/login")
  )

(defn double-auth-page [session]
  (if-let [redirect (double-auth-redirect session)]
    (response/found redirect)
    (auth-view/double-auth (:double-auth-code session))))

;; TODO: Add schema validation
(defn double-auth-check [code session]
  (if-let [redirect (double-auth-redirect session)]
    (response/found redirect)
    (if (= code (:double-auth-code session))
      (-> (response/found "/user/")
          (assoc :session (assoc session :double-authed 1 :double-auth-code nil)))
      ;; TODO: Add error message to double auth
      (response/found "/double-auth"))))

;; TODO: Add schema validation
(defn handle-login [req params]
  (auth-service/login! req params))
