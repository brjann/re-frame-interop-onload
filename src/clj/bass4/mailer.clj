(ns bass4.mailer
  (:require [bass4.config :refer [env]]
            [clojure.java.io :as io]
            [bass4.php_clj.core :refer [clj->php]]
            [clojure.java.shell :as shell]
            [clojure.tools.logging :as log]))

(defn mail*!
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

;; Overwritten by other function when in debug mode
(defn mail!
  ([to subject message]
   (mail*! to subject message nil false))
  ([to subject message reply-to]
   (mail*! to subject message reply-to false)))

(defn mail-debug!
  ([to subject message]
   (mail*! to subject message nil true))
  ([to subject message reply-to]
   (mail*! to subject message reply-to true)))

;; https://github.com/lamuria/email-validator/blob/master/src/email_validator/core.clj
(defn- match-regex?
  "Check if the string matches the regex"
  [v regex]
  (boolean (re-matches regex v)))

(defn is-email?
  "Check if input is a valid email address"
  [input]
  (when (string? input)
    (match-regex? input #"(?i)[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")))