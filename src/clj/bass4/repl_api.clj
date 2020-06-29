(ns bass4.repl-api
  (:require [bass4.services.user :as user-service]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.assessment.statuses :as assessment-statuses]
            [clj-time.core :as t]
            [bass4.now :as now]
            [bass4.clients.core :as clients]
            [bass4.php-interop :as php-interop]
            [bass4.external-messages.sms-sender :as sms]
            [bass4.external-messages.sms-queue :as sms-queue]
            [bass4.external-messages.email-sender :as email]
            [bass4.external-messages.email-queue :as email-queue]
            [bass4.responses.pluggable-ui :as pluggable-ui]
            [bass4.middleware.lockdown :as lockdown]
            [bass4.config :as config]
            [bass4.external-messages.sms-counter :as sms-counter]))

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
                                                              (now/now)
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
                                                               (now/now)
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

(defapi data-for-uid!
  [data]
  (php-interop/data-for-uid! data))

(defapi create-uid-for-session!
  [user-id php-session-id]
  (php-interop/uid-for-data! {:user-id        user-id
                              :php-session-id php-session-id}))

(defapi add-data-to-uid!
  [uid :- [[api/str? 1 50]] data]
  (php-interop/add-data-to-uid! uid data))

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
   redact-text :- [:? [api/str? 1 800]]
   sender-id :- [:? api/->int]]
  (let [db (when-let [db- (get clients/client-db-connections (keyword db-name))]
             @db-)]
    (if db
      (binding [*out* orig-out]
        (sms-queue/queue-1! db user-id to message redact-text sender-id))
      "No such DB")))

(defapi send-email!
  [db-name :- [[api/str? 1 30]]
   to :- [[api/str? 1 200]]
   subject :- [[api/str? 1 200]]
   message :- [[api/str? 1 5000]]]
  (let [db (when-let [db- (get clients/client-db-connections (keyword db-name))]
             @db-)]
    (if db
      (binding [*out* orig-out]
        (boolean (email/send-email-now! db to subject message)))
      "No such DB")))

(defapi queue-email!
  [db-name :- [[api/str? 1 30]]
   user-id :- api/->int
   to :- [[api/str? 1 200]]
   subject :- [[api/str? 1 200]]
   message :- [[api/str? 1 800]]
   redact-text :- [:? [api/str? 1 800]]
   sender-id :- [:? api/->int]]
  (let [db (when-let [db- (get clients/client-db-connections (keyword db-name))]
             @db-)]
    (if db
      (binding [*out* orig-out]
        (email-queue/queue-1! db user-id to subject message redact-text sender-id))
      "No such DB")))

(defapi status-email!
  [db-name :- [[api/str? 1 30]]]
  (if-let [db (when-let [db- (get clients/client-db-connections (keyword db-name))]
                @db-)]
    (let [sms-count (sms-counter/count)
          uid-count (count @php-interop/uids)]
      (email-queue/queue-1! db
                            0
                            (config/env :error-email)
                            "BASS up and running"
                            (str "Number of SMS sent " sms-count "\n"
                                 "Number of UIDs " uid-count)
                            ""
                            0))))

(defapi pluggable-ui?
  [db-name :- [[api/str? 1 30]]]
  (let [db-name-kw (keyword db-name)]
    (if (get clients/client-db-connections db-name-kw)
      (pluggable-ui/pluggable-ui*? db-name-kw)
      "No such DB")))

(defapi locked-down?
  []
  @lockdown/locked-down?)

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