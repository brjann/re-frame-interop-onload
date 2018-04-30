(ns bass4.middleware.debug
  (:require [bass4.sms-sender :as sms]
            [bass4.config :refer [env]]
            [bass4.mailer :refer [mail! mail*! is-email?]]
            [bass4.request-state :as request-state]
            [prone.middleware :refer [wrap-exceptions]]
            [clojure.tools.logging :as log]
            [bass4.services.attack-detector :as a-d]))



;; ----------------
;;  DEBUG REDEFS
;; ----------------

(defn sms-reroute-wrapper
  [sms-fn reroute-sms]
  (fn [recipient message]
    (sms-fn reroute-sms (str message "\n" "To: " recipient))))

(defn mail-reroute-wrapper
  [reroute-email]
  (fn [to subject message & reply-to]
    ;; Must by called with all four args to prevent stack overflow
    (mail*! reroute-email subject (str "To: " to "\n" message) (first reply-to) false)))

(defn sms-reroute-to-mail-wrapper
  [reroute-email]
  (fn [recipient message]
    ;; Must by called with all four args to prevent stack overflow
    (mail*! reroute-email "SMS" (str "To: " recipient "\n" message) nil false)
    (sms/sms-success)))

(defn sms-in-header!
  [recipient message]
  (request-state/swap-state! :debug-headers #(conj %1 (str "SMS to " recipient "\n" message)) [])
  true)

(defn mail-in-header!
  [to subject message & args]
  (request-state/swap-state! :debug-headers #(conj %1 (str "MAIL to " to "\n" subject "\n" message)) [])
  true)

#_(defn- mail-redefs
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
      {#'sms/send-db-sms! sms-in-header!
       #'mail!            mail-in-header!}

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
       #'mail!            (mail-reroute-wrapper mail!)}

      ;; Production environment
      :else
      {}))

(defn- sms-redefs
  []
  (let [sms-reroute (env :dev-reroute-sms)]
    (cond
      ;; Put sms in header when
      ;; - in test environment, or
      ;; - reroute-sms= :header
      (or (env :dev-test)
          (= :header sms-reroute))
      {#'sms/send-db-sms! sms-in-header!}

      (is-email? sms-reroute)
      {#'sms/send-db-sms! (sms-reroute-to-mail-wrapper sms-reroute)}

      (string? sms-reroute)
      {#'sms/send-db-sms! (sms-reroute-wrapper mail! sms-reroute)}

      ;; Production environment
      :else
      {})))

(defn- mail-redefs
  []
  (let [mail-reroute (env :dev-reroute-email)]
    (cond
      ;; Put mail in header when
      ;; - in test environment, or
      ;; - reroute-email = :header
      (or (env :dev-test)
          (= :header mail-reroute))
      {#'mail! mail-in-header!}

      (is-email? mail-reroute)
      {#'mail! (mail-reroute-wrapper mail-reroute)}

      ;; Production environment
      :else
      {})))

(defn debug-redefs
  [handler request]
  ;; Test environment, dev-test and dev are true
  ;; Dev environment, dev-test is false and dev is true
  (let [redefs (merge
                 (mail-redefs)
                 (sms-redefs))]
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