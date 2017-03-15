(ns bass4.responses.auth
  (:require [bass4.services.auth :as auth-service]
            [bass4.views.auth :as auth-view]
            [bass4.services.user :as user]
            [ring.util.http-response :as response]
            [schema.core :as s]))

(defn- not-authenticated? [session]
  (or (nil? (:identity session))
      (nil? (user/get-user (:identity session)))))

#_(defn- double-auth-ok? [session]
  (not (or (nil? (:identity session))
           (nil? (user/get-user (:identity session)))
           (not (auth-service/double-auth-required?))
           (nil? (:double-auth-code session)))))

(defn- not-double-auth-ok? [session]
  (or (not (auth-service/double-auth-required?))
      (nil? (:double-auth-code session))))

(defn- double-auth-done? [session]
  (:double-authed session))

(defn- double-auth-redirect [session]
  (cond
    (not-authenticated? session) "/login"
    (double-auth-done? session) "/user/"
    ;should this be more complex? double auth may be broken
    (not-double-auth-ok? session) "/login")
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

#_(defn double-auth-check [code session]
  (if (double-auth-ok? session)
    (if (= code (:double-auth-code session))
      (-> (response/found "/user/")
          (assoc :session (assoc session :double-authed 1 :double-auth-code nil)))
      (response/found "/double-auth"))
    (response/unauthorized "You are not supposed to be here")))