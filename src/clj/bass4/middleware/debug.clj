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

(defn- sms-void
  [recipient message sender]
  true)

(defn- external-message-out-str
  [& args]
  (println (apply str (interpose "\n" args))))

;; ----------------
;;   EMAIL REDEFS
;; ----------------

(defn- mail-reroute-wrapper
  [reroute-email]
  (fn [to subject message & reply-to]
    (send-email*! reroute-email subject (str "To: " to "\n" message) (first reply-to) false)))

(defn- mail-void
  [to subject message & args]
  true)

(defn external-message-out-str
  [& args]
  (println (apply str (interpose "\n" args))))

(def ^:dynamic *sms-reroute* nil)

(defn sms-redefs
  [sms-reroute]
  (cond
    ;; Put sms in void when
    ;; - in test environment, or
    ;; - reroute-sms= :void
    (or (env :dev-test)
        (= :void sms-reroute))
    {#'sms/send-sms! sms-void}

    (is-email? sms-reroute)
    {#'sms/send-sms! (sms-reroute-to-mail-wrapper sms-reroute)}

    (string? sms-reroute)
    {#'sms/send-sms! (sms-reroute-wrapper sms-reroute)}

    (= :out sms-reroute)
    {#'sms/send-sms! external-message-out-str}

    ;; Production environment
    :else
    {}))

(def ^:dynamic *mail-reroute* nil)

(defn mail-redefs
  [mail-reroute]
  (cond
    ;; Put mail in void when
    ;; - in test environment, or
    ;; - reroute-email = :void
    (or (env :dev-test)
        (= :void mail-reroute))
    {#'send-email! mail-void}

    (is-email? mail-reroute)
    {#'send-email! (mail-reroute-wrapper mail-reroute)}

    (= :out mail-reroute)
    {#'send-email! external-message-out-str}

    ;; Production environment
    :else
    {}))

(defn debug-redefs
  [handler request]
  ;; Test environment, dev-test and dev are true
  ;; Dev environment, dev-test is false and dev is true
  (let [redefs (merge
                 (mail-redefs (or *mail-reroute* (env :dev-reroute-email)))
                 (sms-redefs (or *sms-reroute* (env :dev-reroute-sms))))]
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