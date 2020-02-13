(ns bass4.external-messages.sms-queue
  (:require [bass4.db.core :as db]
            [clojure.java.jdbc :as jdbc]
            [clojure.core.async :refer [thread <!!]]
            [clj-time.coerce :as tc]
            [bass4.external-messages.sms-sender :as sms]
            [clojure.string :as str]
            [bass4.external-messages.email-sender :as email]
            [clojure.tools.logging :as log]))

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
  (when (seq sms-reses)
    (let [sms-statuses (map #(vector (:id %)
                                     "sent"
                                     now
                                     (:provider-id %))
                            sms-reses)]
      (db/external-message-smses-sent! db {:sms-statuses sms-statuses})
      (db/external-message-smses-redact! db {}))))

(defn add!
  [db now smses]
  (when (seq smses)
    (let [sms-vector (map #(vector (:user-id %)
                                   now
                                   "queued"
                                   now
                                   (:to %)
                                   (:message %)
                                   (:redact-text %)
                                   (:sender-id %))
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
                                       (merge sms
                                              {:result      :success
                                               :provider-id res}))
                                     (catch Exception e
                                       (merge sms
                                              {:result    :exception
                                               :exception e}))))
                                 smses))
                     (group-by :result))]
    (when (:exception res)
      (let [final-failed (filter #(<= (dec max-fails) (:fail-count %)) (:exception res))]
        (when (seq final-failed)
          (email/send-error-email! (str "SMS task for " db-name) (str "Send SMS with ids "
                                                                      (str/join " " (map :id final-failed))
                                                                      " failed after " max-fails " tries."))))
      (db-update-fail-count! db now (:exception res))
      (db-final-failed! db))
    (when (:success res)
      (db-smses-sent! db now (:success res)))
    {:exception (when (:exception res) (:exception res))
     :success   (count (:success res))}))