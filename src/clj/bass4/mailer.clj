(ns bass4.mailer
  (:require [bass4.config :refer [env]]
            [clojure.java.io :as io]
            [bass4.php_clj.core :refer [clj->php]]
            [clojure.java.shell :as shell]
            [clojure.tools.logging :as log]))

(defn mail!
  ([to subject message] (mail! to subject message nil))
  ([to subject message reply-to]
   (when (env :dev)
     (log/info (str "Sent mail to " to)))
   (let [mailer-path (io/file (env :bass-path) "system/ExternalMailer.php")
         args        (into {}
                           ;; Removes nil elements
                           (filter second
                                   {"to" to "subject" subject "message" message "reply-to" reply-to}))
         res         (shell/sh "php" (str mailer-path) :in (clj->php args))]
     res
     (when (not= 0 (:exit res))
       (throw (Exception. (str (:out res)))))
     true)))

(defn mail-debug!
  ([to subject message] (mail-debug! to subject message nil))
  ([to subject message reply-to]
   (when (env :dev)
     (log/info (str "Sent mail to " to)))
   (let [mailer-path (io/file (env :bass-path) "system/ExternalMailer.php")
         args        (into {}
                           ;; Removes nil elements
                           (filter second
                                   {"to" to "subject" subject "message" message "reply-to" reply-to "debug" true}))
         res         (shell/sh "php" (str mailer-path) :in (clj->php args))]
     res
     (when (not= 0 (:exit res))
       (throw (Exception. (str (:out res)))))
     (str (:out res)))))

;; https://github.com/lamuria/email-validator/blob/master/src/email_validator/core.clj
(defn- match-regex?
  "Check if the string matches the regex"
  [v regex]
  (boolean (re-matches regex v)))

(defn is-email?
  "Check if input is a valid email address"
  [input]
  (if (nil? input)
    false
    (match-regex? input #"(?i)[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")))