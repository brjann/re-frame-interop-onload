(ns bass4.mailer
  (:require [bass4.config :refer [env]]
            [clojure.java.io :as io]
            [bass4.php_clj.core :refer [clj->php]]))

(defn mail!
  ([to subject message] (mail! to subject message nil))
  ([to subject message reply-to]
   (let [mailer-path (io/file (env :bass-path) "system/ExternalMailer.php")
         args        (into {}
                           (filter second
                                   {"to" to "subject" subject "message" message "reply-to" reply-to}))
         res (clojure.java.shell/sh "php" (str mailer-path) :in (clj->php args))]
     (when (not= 0 {:exit res})
       (throw (Exception. (str (:out res))))))))