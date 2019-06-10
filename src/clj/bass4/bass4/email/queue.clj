(ns bass4.bass4.email.queue
  (:require [bass4.db.core :as db]
            [clojure.java.jdbc :as jdbc]
            [clojure.core.async :refer [thread <!!]]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [bass4.email :as mailer])
  (:import (java.util UUID)))

(defn- db-queue-emails!
  [db email-vector]
  (db/external-message-queue-emails! db {:emails email-vector}))

(defn- db-queued-emails
  [db now]
  (let [res (jdbc/query db ["CALL queued_emails (?);" (tc/to-sql-date now)])]
    (map #(assoc % :status-time now) res)))

(defn queue-emails!
  [db now emails]
  (let [email-vector (map #(vector (:user-id %)
                                   now
                                   "queued"
                                   now
                                   (:to %)
                                   (:subject %)
                                   (:message %)
                                   (or (:reply-to %) ""))
                          emails)]
    (db-queue-emails! db email-vector)))

(defn send-queued-emails!
  [db now]
  (let [emails (db-queued-emails db now)]
    (doall (map #(mailer/send-email-now! (:to %)
                                         (:subject %)
                                         (:message %)
                                         (:reply-to %))))))