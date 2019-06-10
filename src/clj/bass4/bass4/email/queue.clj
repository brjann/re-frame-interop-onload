(ns bass4.bass4.email.queue
  (:require [bass4.db.core :as db]
            [clojure.java.jdbc :as jdbc]
            [clojure.core.async :refer [thread <!!]]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [bass4.email :as mailer]
            [bass4.email :as email]
            [clojure.string :as str])
  (:import (java.util UUID)))

(defn- db-queue-emails!
  [db email-vector]
  (db/external-message-queue-emails! db {:emails email-vector}))

(defn- db-queued-emails
  [db now]
  (let [res (jdbc/query db ["CALL queued_emails (?);" (tc/to-sql-date now)])]
    (map #(assoc % :status-time now) res)))

(defn db-update-fail-count!
  [email-reses]
  (db/external-message-emails-failed! {:ids (map :id email-reses)}))

(defn db-update-fail-count!
  [db now email-reses]
  (db/external-message-emails-update-fail-count! db {:ids  (map :id email-reses)
                                                     :time now}))

(defn db-final-failed!
  [db]
  (db/external-message-emails-final-failed! db {:max-failures 5}))

(defn db-emails-sent!
  [db now email-reses]
  (db/external-message-emails-sent! db {:ids  (map :id email-reses)
                                        :time now}))

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
  [db-name now]
  (let [db     @(get db/db-connections db-name)
        emails (db-queued-emails db now)
        res    (->> (doall (map (fn [email]
                                  (try
                                    (let [res (mailer/send-email-now! db
                                                                      (:to email)
                                                                      (:subject email)
                                                                      (:message email)
                                                                      (:reply-to email))]
                                      {:id     (:id email)
                                       :result (if res :success :fail)})
                                    (catch Exception e
                                      {:id        (:id email)
                                       :result    :exception
                                       :exception e})))
                                emails))
                    (group-by :result))]
    (when (:exception res)
      (log/error (:exception res))
      (log/debug (map :id (:exception res)))
      (email/send-error-email! (str "Mailer task for " db-name) (str "Could not send email with ids "
                                                                     (str/join " " (map :id (:exception res)))))
      (db-update-fail-count! db now (:exception res))
      (db-final-failed! db))
    (when (:fail res)
      (db-update-fail-count! db now (:fail res))
      (db-final-failed! db))
    (when (:success res)
      (db-emails-sent! db now (:success res)))
    {:exception (count (:exception res))
     :fail      (count (:fail res))
     :success   (count (:success res))}))