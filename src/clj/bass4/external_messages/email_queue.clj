(ns bass4.external-messages.email-queue
  (:require [bass4.db.core :as db]
            [clojure.java.jdbc :as jdbc]
            [clojure.core.async :refer [thread <!!]]
            [clojure.tools.logging :as log]
            [clj-time.coerce :as tc]
            [bass4.external-messages.email-sender :as mailer]
            [bass4.external-messages.email-sender :as email]
            [clojure.string :as str]))

(defn- db-queue-emails!
  [db email-vector]
  (db/external-message-queue-emails! db {:emails email-vector}))

(defn- db-queued-emails
  [db now]
  (jdbc/with-db-connection [db db]
    (jdbc/with-db-transaction [db db]
      (let [res (jdbc/query db (str "SELECT * FROM external_message_email "
                                    "WHERE `status` = 'queued' FOR UPDATE;"))]
        (jdbc/execute! db [(str "UPDATE external_message_email "
                                "SET `status` = 'sending', `status-time` = ?"
                                "WHERE `status` = 'queued';")
                           (tc/to-sql-date now)])
        res))))

(defn db-update-fail-count!
  [db now email-reses]
  (db/external-message-emails-update-fail-count! db {:ids  (map :id email-reses)
                                                     :time now}))

(def ^:dynamic max-fails 20)
(defn db-final-failed!
  [db]
  (db/external-message-emails-final-failed! db {:max-fails max-fails}))

(defn db-emails-sent!
  [db now email-reses]
  (db/external-message-emails-sent! db {:ids  (map :id email-reses)
                                        :time now}))

(defn add!
  [db now emails]
  (when (seq emails)
    (let [email-vector (map #(vector (:user-id %)
                                     now
                                     "queued"
                                     now
                                     (:to %)
                                     (:subject %)
                                     (:message %)
                                     (or (:reply-to %) ""))
                            emails)]
      (db-queue-emails! db email-vector))))

(defn send!
  [db local-config now]
  (let [db-name (:name local-config)
        emails  (db-queued-emails db now)
        res     (->> (doall (map (fn [email]
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
    {:exception (when (:exception res) (:exception res))
     :fail      (count (:fail res))
     :success   (count (:success res))}))