(ns bass4.middleware.errors
  (:require [bass4.layout :as layout]
            [bass4.config :refer [env]]
            [bass4.utils :refer [nil-zero? subs+]]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]
            [bass4.mailer :refer [mail!]]
            [clojure.tools.logging :as log]
            [bass4.layout :refer [*app-context* error-page error-400-page]]
            [bass4.request-state :as request-state])
  (:import (clojure.lang ExceptionInfo)))



(defn on-error [request response]
  (layout/error-403-page (get-in request [:session :identity]))
  #_(error-page
      {:status 403
       :title (str "Access to " (:uri request) " is not authorized")}))

(defn wrap-restricted [handler]
  (restrict handler {:handler authenticated?
                     :on-error on-error}))


(defn mail-error!
  [req-state]
  (try
    (mail!
      (env :email-error)
      "Error in BASS4"
      (str "Sent by " (:name req-state) "\n" (:error-messages req-state)))
    (catch Exception x
      (log/error "Could not send error email to: " (env :email-error) "\nError: " x))))

(defn catch-internal-error
  [handler req]
  (try
    (handler req)
    (catch Throwable t
      (log/error t)
      (request-state/record-error! t)
      (error-page {:status  500
                   :title   "Something bad happened!"
                   :message (str "Try reloading the page or going back in your browser. Please contact " (env :email-admin) " if the problem persists.")}))))

(defn internal-error-wrapper
  [handler req]
  (let [res       (catch-internal-error handler req)
        req-state (request-state/get-state)]
    ;; Email errors
    (when-not (nil-zero? (:error-count req-state))
      (mail-error! req-state))
    res))

(defn wrap-internal-error [handler]
  (fn [req]
    (internal-error-wrapper handler req)))



(defn wrap-schema-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch ExceptionInfo e
        (if (or
              (= (:type (.data e)) :schema.core/error)
              (= "400" (subs+ (.getMessage e) 0 3)))
          (do
            (request-state/record-error! (.getMessage e))
            (error-400-page (when (env :debug-mode (.getMessage e)))))
          (throw e))))))