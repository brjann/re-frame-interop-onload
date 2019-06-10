(ns bass4.middleware.errors
  (:require [bass4.config :refer [env]]
            [bass4.utils :refer [nil-zero?]]
            [bass4.email :as email]
            [clojure.tools.logging :as log]
            [bass4.error-pages :as error-pages]
            [bass4.api-coercion :as api]
            [bass4.middleware.request-logger :as request-logger])
  (:import (clojure.lang ExceptionInfo)))

(defn mail-request-error!
  [req-state]
  (try
    (email/async-email!
      nil
      (env :error-email)
      "Error in BASS4"
      (str "Sent by " (:name req-state) "\n" (:error-messages req-state)))
    (catch Exception x
      (log/error "Could not send error email to: " (env :error-email)
                 "\nError message" (:error-messages req-state)
                 "\nMail error: " x))))

(defn catch-request-error
  [handler req]
  (try
    (handler req)
    (catch Throwable t
      (log/error t)
      (request-logger/record-error! t)
      (error-pages/error-page {:status  500
                               :title   "Something bad happened!"
                               :message (str "Try reloading the page or going back in your browser. Please contact " (env :email-admin) " if the problem persists.")}))))

(defn catch-internal-error-mw
  [handler request]
  (let [res       (catch-request-error handler request)
        req-state (request-logger/get-state)]
    ;; Email errors
    (when-not (nil-zero? (:error-count req-state))
      (mail-request-error! req-state))
    res))

(defn wrap-api-error
  [handler request]
  (try
    (handler request)
    (catch ExceptionInfo e
      (let [data (.data e)
            type (:type data)]
        (if (or
              (= :bass4.api-coercion/api-exception type)
              (= :schema.core/error type)
              (and (= :http-error type)
                   (= 400 (:status data))))
          (api/api-exception-response e)
          (throw e))))))