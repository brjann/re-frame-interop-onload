(ns bass4.external-messages.email-sender
  (:require [bass4.config :refer [env]]
            [clojure.java.io :as io]
            [bass4.php_clj.core :refer [clj->php]]
            [clojure.java.shell :as shell]
            [clojure.tools.logging :as log]
            [bass4.external-messages.async :as external-messages]
            [clojure.core.async :refer [chan go <! put!]]
            [bass4.db-config :as db-config]
            [bass4.services.bass :as bass]
            [bass4.db.core :as db]
            [bass4.utils :as utils]
            [bass4.config :as config])
  (:import (clojure.core.async.impl.channels ManyToManyChannel)))



(defn- db-no-reply-address
  [db]
  (:no-reply-address (db/external-message-no-reply-address db {})))

;; ---------------------
;;  ACTUAL EMAIL SENDER
;; ---------------------

(defn send-email*!
  ([to subject message sender reply-to debug?]
   (when (env :dev)
     (log/info (str "Sent mail to " to)))
   (let [mailer-path (io/file (env :bass-path) "system/ExternalMailer.php")
         args        (into {}
                           ;; Removes nil elements
                           (filter second
                                   {"to"       to
                                    "subject"  subject
                                    "message"  message
                                    "sender"   sender
                                    "reply-to" reply-to
                                    "debug"    debug?}))
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
                          (cond
                            (string? re-route)
                            :redirect

                            (instance? ManyToManyChannel re-route)
                            :chan

                            :else
                            re-route))))

(defmethod send-email! :redirect
  [to subject message & reply-to]
  (let [reroute-email (or *email-reroute* (env :dev-reroute-email))]
    (send-email*! reroute-email subject (str "To: " to "\n" message) (first reply-to) (config/env :no-reply-address) false)))

(defmethod send-email! :void
  [& _]
  true)

(defmethod send-email! :out
  [& more]
  (println (apply str (interpose "\n" (conj more "email"))))
  true)

(defmethod send-email! :exception
  [& _]
  (throw (Exception. "An exception")))

(defmethod send-email! :chan
  [& more]
  (let [c *email-reroute*]
    (put! c more))
  true)

(defmethod send-email! :default
  ([to subject message sender]
    (send-email*! to subject message sender nil false))
  ([to subject message sender reply-to]
    (send-email*! to subject message sender reply-to false)))

;; -------------------------------------
;;   EXTERNAL MESSAGE METHOD FOR EMAIL
;; -------------------------------------

(defmethod external-messages/async-message-sender :email
  [{:keys [to subject message sender reply-to]}]
  (send-email! to subject message sender reply-to))

;; ---------------------
;;    ERROR EMAIL
;; ---------------------

(defn error-sender
  [subject message]
  (send-email!
    (env :error-email)
    subject
    message
    (env :no-reply-address)))

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
  ([db to subject message]
   (send-email-now! db to subject message nil))
  ([db to subject message reply-to]
   (when-not (is-email? to)
     (throw (Exception. (str "Not valid email address: " to))))
   (let [sender (if db
                  (db-no-reply-address db)
                  (config/env :no-reply-address))]
     (let [res (send-email! to subject message sender reply-to)]
       (assert (boolean? res))
       (when res
         (if db
           (bass/inc-email-count! db)
           (log/info "No DB selected for email count update.")))
       res))))

(defn async-email!
  "Throws if to is not valid email address.
  Returns channel on which send result will be put.
  Guarantees that update of external message count is done before putting result"
  ([db to subject message]
   (async-email! db to subject message nil))
  ([db to subject message reply-to]
   (when-not (is-email? to)
     (throw (Exception. (str "Not valid email address: " to))))
   (let [sender     (if db
                      (db-no-reply-address db)
                      (config/env :no-reply-address))
         error-chan (external-messages/async-error-chan error-sender (db-config/db-name))
         own-chan   (external-messages/queue-message! {:type       :email
                                                       :to         to
                                                       :subject    subject
                                                       :message    message
                                                       :sender     sender
                                                       :reply-to   reply-to
                                                       :error-chan error-chan})]
     (go
       (let [res (<! own-chan)]
         (when-not (= :error (:result res))
           (if db
             (bass/inc-email-count! db)
             (log/info "No DB selected for email count update.")))
         ;; Res is result of go block
         res)))))


(defn send-error-email!
  [sender-name message]
  (try
    (async-email!
      nil
      (env :error-email)
      "Error in BASS4"
      (str "Sent by " sender-name "\n" message))
    (catch Exception x
      (log/error "Could not send error email to: " (env :error-email)
                 "\nError message" message
                 "\nMail error: " x))))