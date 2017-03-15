(ns bass4.responses.auth
  (:require [bass4.services.auth :as auth-service]
            [bass4.views.auth :as auth-view]
            [bass4.services.user :as user]
            [ring.util.http-response :as response]
            [schema.core :as s]))

;; Confused over whether much of this logic should be moved to the service
(defn- double-auth-ok? [session]
  (not (or (nil? (:identity session))
           (nil? (user/get-user (:identity session)))
           (not (auth-service/double-auth-required?))
           (nil? (:double-auth-code session)))))

(defn double-auth-page [session]
  (if (double-auth-ok? session)
    (auth-view/double-auth (:double-auth-code session))
    (response/unauthorized "You are not supposed to be here")))

(defn double-auth-check [code session]
  (if (double-auth-ok? session)
    (if (= code (:double-auth-code session))
      (-> (response/found "/user/")
          (assoc :session (assoc session :double-authed 1 :double-auth-code nil)))
      (response/found "/double-auth"))
    (response/unauthorized "You are not supposed to be here")))