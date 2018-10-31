(ns bass4.email
  (:require [bass4.config :refer [env]]
            [clojure.java.io :as io]
            [bass4.php_clj.core :refer [clj->php]]
            [clojure.java.shell :as shell]
            [clojure.tools.logging :as log]
            [bass4.external-messages :as external-messages]
            [clojure.core.async :refer [chan go <!]]
            [bass4.db-config :as db-config]))

(defn send-email*!
  ([to subject message reply-to debug?]
   (when (env :dev)
     (log/info (str "Sent mail to " to)))
   (let [mailer-path (io/file (env :bass-path) "system/ExternalMailer.php")
         args        (into {}
                           ;; Removes nil elements
                           (filter second
                                   {"to" to "subject" subject "message" message "reply-to" reply-to "debug" debug?}))
         res         (shell/sh "php" (str mailer-path) :in (clj->php args))]
     res
     (when (not= 0 (:exit res))
       (throw (Exception. (str (:out res)))))
     (if debug?
       (str (:out res))
       true))))

;; Overwritten by other function when in debug/test mode
(defn ^:dynamic send-email!
  ([to subject message]
   (send-email*! to subject message nil false))
  ([to subject message reply-to]
   (send-email*! to subject message reply-to false)))


;; https://github.com/lamuria/email-validator/blob/master/src/email_validator/core.clj
(defn is-email?
  "Check if input is a valid email address"
  [input]
  (let [match-regex? (fn [v regex]
                       (boolean (re-matches regex v)))]
    (when (string? input)
      (match-regex? input #"(?i)[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"))))

;; Bind the function to local var and close over it,
;; to respect dynamic bindings.
;; Since the send function is executed in another thread
(defmethod external-messages/external-message-sender :email
  [{:keys [to subject message reply-to]}]
  (let [email-sender send-email!]
    (fn [] (email-sender to subject message reply-to))))

(defn error-sender
  [subject message]
  (send-email!
    (env :error-email)
    subject
    message))

(defn queue-email!
  ([to subject message]
   (queue-email! to subject message nil))
  ([to subject message reply-to]
   (let [error-chan (external-messages/async-error-chan error-sender (db-config/db-name))]
     (external-messages/queue-message! {:type       :email
                                        :to         to
                                        :subject    subject
                                        :message    message
                                        :reply-to   reply-to
                                        :error-chan error-chan}))))