(ns bass4.email
  (:require [bass4.config :refer [env]]
            [clojure.java.io :as io]
            [bass4.php_clj.core :refer [clj->php]]
            [clojure.java.shell :as shell]
            [clojure.tools.logging :as log]
            [bass4.external-messages :as external-messages]
            [clojure.core.async :refer [chan go <!]]
            [bass4.db-config :as db-config]
            [bass4.services.bass :as bass]
            [bass4.db.core :as db]
            [bass4.utils :as utils]))

;; ---------------------
;;  ACTUAL EMAIL SENDER
;; ---------------------

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


;; ---------------------
;;    EMAIL REROUTING
;; ---------------------


(def ^:dynamic *email-reroute* nil)

(defmulti send-email! (fn [& more]
                        (let [re-route (or *email-reroute* (env :dev-reroute-email) :default)]
                          (if (string? re-route)
                            :redirect
                            re-route))))

(defmethod send-email! :redirect
  [to subject message & reply-to]
  (let [reroute-email (or *email-reroute* (env :dev-reroute-email))]
    (send-email*! reroute-email subject (str "To: " to "\n" message) (first reply-to) false)))

(defmethod send-email! :void
  [& _])

(defmethod send-email! :out
  [& more]
  (println (apply str (interpose "\n" (conj more "email")))))

(defmethod send-email! :default
  ([to subject message]
    (send-email*! to subject message nil false))
  ([to subject message reply-to]
    (send-email*! to subject message reply-to false)))

;; -------------------------------------
;;   EXTERNAL MESSAGE METHOD FOR EMAIL
;; -------------------------------------

(defmethod external-messages/external-message-sender :email
  [{:keys [to subject message reply-to]}]
  (send-email! to subject message reply-to))

;; ---------------------
;;    ERROR EMAIL
;; ---------------------

(defn error-sender
  [subject message]
  (send-email!
    (env :error-email)
    subject
    message))

;; ---------------------
;;     EMAIL API
;; ---------------------

;; https://github.com/lamuria/email-validator/blob/master/src/email_validator/core.clj
(defn is-email?
  "Check if input is a valid email address"
  [input]
  (when (string? input)
    (utils/match-regex? input #"(?i)[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")))

(defn send-email-now!
  ([to subject message]
   (send-email-now! to subject message nil))
  ([to subject message reply-to]
   (when-not (is-email? to)
     (throw (Exception. (str "Not valid email address: " to))))
   (send-email! to subject message reply-to)
   (if db/*db*
     (bass/inc-email-count! db/*db*)
     (log/info "No DB selected for email count update."))
   true))

(defn queue-email!
  "Throws if to is not valid email address.
  Returns channel on which send result will be put.
  Guarantees that update of external message count is done before putting result"
  ([to subject message]
   (queue-email! to subject message nil))
  ([to subject message reply-to]
   (when-not (is-email? to)
     (throw (Exception. (str "Not valid email address: " to))))
   (let [error-chan (external-messages/async-error-chan error-sender (db-config/db-name))
         own-chan   (external-messages/queue-message! {:type       :email
                                                       :to         to
                                                       :subject    subject
                                                       :message    message
                                                       :reply-to   reply-to
                                                       :error-chan error-chan})]
     (go
       (let [res (<! own-chan)]
         (when-not (= :error (:result res))
           (if db/*db*
             (bass/inc-email-count! db/*db*)
             (log/info "No DB selected for email count update.")))
         ;; Res is result of go block
         res)))))
