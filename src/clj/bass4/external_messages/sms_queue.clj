(ns bass4.external-messages.sms-queue
  (:require [bass4.db.core :as db]
            [clojure.java.jdbc :as jdbc]
            [clojure.core.async :refer [thread <!!]]
            [clojure.tools.logging :as log]
            [clj-time.coerce :as tc]
            [bass4.external-messages.sms-sender :as sms]
            [clojure.string :as str]
            [bass4.external-messages.email-sender :as mailer]))

(defn- db-queue-smses!
  [db sms-vector]
  (db/external-message-queue-smses! db {:smses sms-vector}))

(defn- db-queued-smses
  [db now]
  (jdbc/with-db-connection [db db]
    (jdbc/with-db-transaction [db db]
      (let [res (jdbc/query db (str "SELECT * FROM external_message_sms "
                                    "WHERE `status` = 'queued' FOR UPDATE;"))]
        (jdbc/execute! db [(str "UPDATE external_message_sms "
                                "SET `status` = 'sending', `status-time` = ?"
                                "WHERE `status` = 'queued';")
                           (tc/to-sql-date now)])
        res))))

(defn db-update-fail-count!
  [db now sms-reses]
  (db/external-message-smses-update-fail-count! db {:ids  (map :id sms-reses)
                                                    :time now}))

(def ^:dynamic max-fails 20)
(defn db-final-failed!
  [db]
  (db/external-message-smses-final-failed! db {:max-fails max-fails}))

(defn db-smses-sent!
  [db now sms-reses]
  (db/external-message-smses-sent! db {:ids  (map :id sms-reses)
                                       :time now}))

(defn add!
  [db now smses]
  (when (seq smses)
    (let [sms-vector (map #(vector (:user-id %)
                                   now
                                   "queued"
                                   now
                                   (:to %)
                                   (:message %))
                          smses)]
      (db-queue-smses! db sms-vector))))

(defn send!
  [db local-config now]
  (let [db-name (:name local-config)
        smses   (db-queued-smses db now)
        res     (->> (doall (map (fn [sms]
                                   (try
                                     (let [res (sms/send-sms-now! db
                                                                  (:to sms)
                                                                  (:message sms))]
                                       {:id     (:id sms)
                                        :result (if res :success :fail)})
                                     (catch Exception e
                                       {:id        (:id sms)
                                        :result    :exception
                                        :exception e})))
                                 smses))
                     (group-by :result))]
    (when (:exception res)
      (log/error (:exception res))
      (log/debug (map :id (:exception res)))
      (mailer/send-error-email! (str "Mailer task for " db-name) (str "Could not send sms with ids "
                                                                      (str/join " " (map :id (:exception res)))))
      (db-update-fail-count! db now (:exception res))
      (db-final-failed! db))
    (when (:fail res)
      (db-update-fail-count! db now (:fail res))
      (db-final-failed! db))
    (when (:success res)
      (db-smses-sent! db now (:success res)))
    {:exception (when (:exception res) (:exception res))
     :fail      (count (:fail res))
     :success   (count (:success res))}))