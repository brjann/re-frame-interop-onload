(ns bass4.middleware.debug
  (:require [bass4.sms-sender :as sms]
            [bass4.config :refer [env]]
            [bass4.email :refer [send-email! send-email*! is-email?]]
            [bass4.request-state :as request-state]
            [prone.middleware :refer [wrap-exceptions]]
            [bass4.db.core :as db]
            [clojure.tools.logging :as log]
            [bass4.db-config :as db-config]))



;; ----------------
;;  NEW SMS REDEFS
;; ----------------

(defn- sms-reroute-wrapper
  [reroute-sms]
  (fn [recipient message sender]
    (sms/send-sms*! reroute-sms (str message "\n" "To: " recipient) sender)))

(defn- sms-reroute-to-mail-wrapper
  [reroute-email]
  (fn [recipient message sender]
    (send-email*! reroute-email "SMS" (str "To: " recipient "\n" message) nil false)))

(defn- sms-do-nothing!
  [recipient message sender]
  true)



;; ----------------
;;   EMAIL REDEFS
;; ----------------

(defn- mail-reroute-wrapper
  [reroute-email]
  (fn [to subject message & reply-to]
    (send-email*! reroute-email subject (str "To: " to "\n" message) (first reply-to) false)))

(defn- mail-in-header!
  [to subject message & args]
  (request-state/swap-state! :debug-headers #(conj %1 (str "MAIL to " to "\n" subject "\n" message)) [])
  (log/debug "MAIL TO" to "SUBJECT" subject "MESSAGE" message)
  true)

(def ^:dynamic *sms-reroute* nil)

(defn- sms-redefs
  []
  (let [sms-reroute (or *sms-reroute*
                        (env :dev-reroute-sms))]
    (cond
      ;; Put sms in header when
      ;; - in test environment, or
      ;; - reroute-sms= :header
      (or (env :dev-test)
          (= :header sms-reroute))
      {#'sms/send-sms! sms-do-nothing!}

      (is-email? sms-reroute)
      {#'sms/send-sms! (sms-reroute-to-mail-wrapper sms-reroute)}

      (string? sms-reroute)
      {#'sms/send-sms! (sms-reroute-wrapper sms-reroute)}

      ;; Production environment
      :else
      {})))

(def ^:dynamic *mail-reroute* nil)

(defn- mail-redefs
  []
  (let [mail-reroute (or *mail-reroute*
                         (env :dev-reroute-email))]
    (cond
      ;; Put mail in header when
      ;; - in test environment, or
      ;; - reroute-email = :header
      (or (env :dev-test)
          (= :header mail-reroute))
      {#'send-email! mail-in-header!}

      (is-email? mail-reroute)
      {#'send-email! (mail-reroute-wrapper mail-reroute)}

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
      (with-bindings redefs
        (handler request))
      (handler request))))

(defn wrap-debug-exceptions
  [handler]
  (fn [request]
    (if (db-config/debug-mode?)
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
    (if (db-config/debug-mode?)
      (session-modification-wrapper handler request)
      (handler request))))