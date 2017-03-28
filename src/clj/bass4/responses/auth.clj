(ns bass4.responses.auth
  (:require [bass4.services.auth :as auth-service]
            [bass4.views.auth :as auth-view]
            [bass4.services.user :as user]
            [ring.util.http-response :as response]
            [schema.core :as s]
            [clojure.tools.logging :as log]))

(defn- double-auth-redirect [session]
  (cond
    (auth-service/not-authenticated? session) "/login"
    (auth-service/double-auth-done? session) "/user/"
    ;should this be more complex? double auth may be broken
    (auth-service/not-double-auth-ok? session) "/login"))

(defn re-auth440
  ([] re-auth440 "")
  ([body]
   {:status 440
    :headers {}
    :body body}))

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
(defn handle-login [request params]
  (auth-service/login! request params))

(defn re-auth [session]
  (if (:auth-timeout session)
    (auth-view/re-auth-page)
    (if (:identity session)
      (response/found "/user/")
      (response/found "/login"))))

;; TODO: Add errors
(defn check-re-auth [session password]
  (when (:auth-timeout session)
    (when-let [user-id (:identity session)]
      (if (auth-service/authenticate-by-user-id user-id password)
        (-> (response/found "/user/")
            (assoc :session (merge session {:auth-timeout nil})))
        (auth-view/re-auth-page)))))

(defn check-re-auth-ajax [session password]
  (when (:auth-timeout session)
    (when-let [user-id (:identity session)]
      (if (auth-service/authenticate-by-user-id user-id password)
        (-> (response/ok)
            (assoc :session (merge session {:auth-timeout nil})))
        (re-auth440 "password")))))