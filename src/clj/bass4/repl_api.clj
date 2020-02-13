(ns bass4.repl-api
  (:require [bass4.services.user :as user-service]
            [bass4.db.core :as db]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.assessment.statuses :as assessment-statuses]
            [clj-time.core :as t]
            [bass4.clients.core :as clients]
            [bass4.php-interop :as php-interop]
            [bass4.external-messages.sms-sender :as sms]
            [bass4.external-messages.sms-queue :as sms-queue]))

(defonce orig-out *out*)

(defapi hash-password
  [password :- [[api/str? 1 100]]]
  (user-service/password-hasher password))

(defapi user-assessment-statuses
  [db-name :- [[api/str? 1 30]] user-id :- api/->int]
  (let [db (when-let [db- (get clients/client-db-connections (keyword db-name))]
             @db-)]
    (if db
      (->> (assessment-statuses/user-administrations-statuses db
                                                              (t/now)
                                                              user-id)
           (map (fn [assessment]
                  {"assessment-id"     (:assessment-id assessment)
                   "assessment-index"  (:assessment-index assessment)
                   "administration-id" (:participant-administration-id assessment)
                   "status"            (name (:status assessment))})))
      "No such DB")))

(defapi group-assessment-statuses
  [db-name :- [[api/str? 1 30]] group-id :- api/->int]
  (let [db (when-let [db- (get clients/client-db-connections (keyword db-name))]
             @db-)]
    (if db
      (->> (assessment-statuses/group-administrations-statuses db
                                                               (t/now)
                                                               group-id)
           (map (fn [assessment]
                  {"assessment-id"     (:assessment-id assessment)
                   "assessment-index"  (:assessment-index assessment)
                   "administration-id" (:group-administration-id assessment)
                   "status"            (name (:status assessment))})))
      "No such DB")))

(defapi uid-for-data!
  [data]
  (php-interop/uid-for-data! data))

(defapi send-sms!
  [db-name :- [[api/str? 1 30]] to :- [[api/str? 1 30]] message :- [[api/str? 1 800]]]
  (let [db (when-let [db- (get clients/client-db-connections (keyword db-name))]
             @db-)]
    (if db
      (binding [*out* orig-out]
        (boolean (sms/send-sms-now! db to message)))
      "No such DB")))

(defapi queue-sms!
  [db-name :- [[api/str? 1 30]]
   user-id :- api/->int
   to :- [[api/str? 1 30]]
   message :- [[api/str? 1 800]]
   redact-text :- [[api/str? 1 800]]
   sender-id :- api/->int]
  (let [db (when-let [db- (get clients/client-db-connections (keyword db-name))]
             @db-)]
    (if db
      (binding [*out* orig-out]
        (sms-queue/add! db (t/now) [{:user-id     user-id
                                     :to          to
                                     :message     message
                                     :redact-text redact-text
                                     :sender-id   sender-id}]))
      "No such DB")))

(defapi mirror
  [arg :- [[api/str? 0 1000]]]
  arg)


#_(defapi load-test
    [arg :- [[api/str? 0 1000]]]
    (log/debug "Start request for" arg)
    (Thread/sleep (rand-int 5000))
    (let [x [arg
             (apply str (repeat (+ 10000 (rand-int 20000)) "X"))]]
      (log/debug "End request for" arg)
      x))