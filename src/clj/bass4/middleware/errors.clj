(ns bass4.middleware.errors
  (:require [bass4.layout :as layout]
            [bass4.config :refer [env]]
            [bass4.utils :refer [nil-zero?]]
            [clojure.string :as string]
            [bass4.mailer :refer [mail!]]
            [clojure.tools.logging :as log]
            [bass4.layout :refer [error-page error-400-page]]
            [bass4.request-state :as request-state]
            [clojure.string :as string]
            [bass4.db-config :as db-config]
            [ring.util.http-response :as http-response])
  (:import (clojure.lang ExceptionInfo)))

(defn mail-error!
  [req-state]
  (try
    (mail!
      (env :error-email)
      "Error in BASS4"
      (str "Sent by " (:name req-state) "\n" (:error-messages req-state)))
    (catch Exception x
      (log/error "Could not send error email to: " (env :error-email) "\nError: " x))))

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

(defn internal-error
  [handler req]
  (let [res       (catch-internal-error handler req)
        req-state (request-state/get-state)]
    ;; Email errors
    (when-not (nil-zero? (:error-count req-state))
      (mail-error! req-state))
    res))

(defn wrap-api-error [handler request]
  (try
    (handler request)
    (catch ExceptionInfo e
      (if (or
            (= (:type (.data e)) :bass4.api-coercion/api-exception)
            (= (:type (.data e)) :schema.core/error)
            (string/starts-with? (.getMessage e) "400"))
        (do
          (let [msg (.getMessage e)]
            (log/error msg)
            (request-state/record-error! msg))
          (error-400-page (when (db-config/debug-mode?) (.getMessage e))))
        (throw e)))))


(defn wrap-schema-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch ExceptionInfo e
        (if (or
              (= (:type (.data e)) :schema.core/error)
              (string/starts-with? (.getMessage e) "400"))
          (do
            (request-state/record-error! (.getMessage e))
            (error-400-page (when (db-config/debug-mode?) (.getMessage e))))
          (throw e))))))