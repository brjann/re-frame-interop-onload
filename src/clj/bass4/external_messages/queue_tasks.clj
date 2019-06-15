(ns bass4.external-messages.queue-tasks
  (:require [bass4.external-messages.email-queue :as email-queue]
            [bass4.external-messages.sms-queue :as sms-queue]))


(defn task-res
  [res]
  (assoc res :cycles (+ (:fail res) (:success res))))

(defn email-task
  [db local-config now]
  (-> (email-queue/send! db local-config now)
      (task-res)))

(defn sms-task
  [db local-config now]
  (-> (sms-queue/send! db local-config now)
      (task-res)))
