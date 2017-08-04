(ns bass4.middleware.debug-redefs
  (:require [bass4.sms-sender :as sms]
            [bass4.config :refer [env]]
            [bass4.mailer :refer [mail!]]
            [bass4.request-state :as request-state]))



;; ----------------
;;  DEBUG REDEFS
;; ----------------

(defn debug-send-sms!
  [recipient message]
  (when (mail! (env :email-error) "SMS" (str "To: " recipient "\n" message))
    (sms/sms-success)))

(defn debug-wrap-mail-fn
  [mailer-fn]
  (fn [to subject message & args]
    ;; Must by called with all four args to prevent stack overflow
    (mailer-fn (env :email-error) subject (str "To: " to "\n" message) nil)))

(defn test-send-sms!
  [recipient message]
  (request-state/swap-state! :debug-headers #(conj %1 (str "SMS to " recipient "\n" message)) [])
  true)

(defn test-mail!
  [to subject message & args]
  (request-state/swap-state! :debug-headers #(conj %1 (str "MAIL to " to "\n" subject "\n" message)) [])
  true)


(defn debug-redefs-wrapper
  [handler request]
  (cond
    ;; Test environment
    (or (env :dev-test) (env :dev))
    (with-redefs [sms/send-db-sms! test-send-sms!
                  mail! test-mail!]
      (handler request))

    ;; Debug or development environment
    (env :debug)
    (with-redefs [sms/send-db-sms! debug-send-sms!
                  mail! (debug-wrap-mail-fn mail!)]
      (handler request))

    ;; Production environment
    :else
    (handler request)))


(defn wrap-debug-redefs
  [handler]
  (fn [request]
    (debug-redefs-wrapper handler request)))

