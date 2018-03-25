(ns bass4.middleware.debug
  (:require [bass4.sms-sender :as sms]
            [bass4.config :refer [env]]
            [bass4.mailer :refer [mail!]]
            [bass4.request-state :as request-state]
            [prone.middleware :refer [wrap-exceptions]]
            [clojure.tools.logging :as log]
            [bass4.services.attack-detector :as a-d]))



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

(defn- mail-redefs
  []
  (cond
    ;; Put mail and sms in header in
    ;; - test environment
    ;; - dev environment unless
    ;;   :dev-allow-email or :dev-allow-external-messages are true
    (or (env :dev-test)
        (and (env :dev)
             (not (or
                    (env :dev-allow-email)
                    (env :dev-allow-external-messages)))))
    {#'sms/send-db-sms! test-send-sms!
     #'mail!            test-mail!}

    ;; Send mail and sms to debug email in
    ;; - debug mode unless :dev-allow-external-messages is true
    ;; - dev environment if :dev-allow-email is true
    ;;   unless :dev-allow-external-messages is true
    (or (and (env :debug)
             (not (env :dev-allow-external-messages)))
        (and (env :dev)
             (env :dev-allow-email)
             (not (env :dev-allow-external-messages))))
    {#'sms/send-db-sms! debug-send-sms!
     #'mail!            (debug-wrap-mail-fn mail!)}

    ;; Production environment
    :else
    {}))

(defn debug-redefs
  [handler request]
  ;; Test environment, dev-test and dev are true
  ;; Dev environment, dev-test is false and dev is true
  (let [redefs (merge
                 (mail-redefs))]
    (if redefs
      (with-redefs-fn redefs
        #(handler request))
      (handler request))))

(defn wrap-debug-exceptions
  [handler]
  (fn [request]
    (if (or (env :debug-mode) (env :dev))
      ((wrap-exceptions handler) request)
      (handler request))))

(def ^:dynamic *session-modification* nil)
(defn session-modification-wrapper
  [handler request]
  (if *session-modification*
    (let [session  (merge (:session request) *session-modification*)
          response (handler (assoc request :session session))]
      (assoc response :session (if (nil? (:session response))
                                 session
                                 (merge (:session response) *session-modification*))))
    (handler request)))

(defn wrap-session-modification
  [handler]
  (fn [request]
    (if (or (env :debug-mode) (env :dev))
      (session-modification-wrapper handler request)
      (handler request))))