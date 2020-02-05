(ns bass4.test.admin-reminder
  (:require [clj-time.core :as t]
            [bass4.test.core :refer :all]
            [clojure.test :refer :all]
            [bass4.task.admin-reminder :as admin-reminder]
            [bass4.services.user :as user-service]
            [bass4.test.assessment-utils :refer [project-ass1-id]]
            [bass4.db.core :as db]
            [bass4.services.messages :as messages]
            [clojure.tools.logging :as log]
            [bass4.utils :as utils]
            [clojure.java.jdbc :as jdbc]
            [bass4.services.bass :as bass-service]
            [clj-time.coerce :as tc]))

(use-fixtures
  :once
  test-fixtures)

(defn clear-flags!
  []
  (jdbc/execute! db/*db* ["TRUNCATE c_flag"]))

(defn clear-messages!
  []
  (jdbc/execute! db/*db* ["TRUNCATE c_message"]))

(defn clear-homework!
  []
  (jdbc/execute! db/*db* ["TRUNCATE content_data_homework"]))

(defn set-message-properties
  [message-id send-time read?]
  (bass-service/update-object-properties*! db/*db*
                                           "c_message"
                                           message-id
                                           {"SendTime" (utils/to-unix send-time)
                                            "ReadTime" (if read? 1 0)
                                            "Draft"    0}))

(defn check-messages
  [time-limit]
  (let [res (admin-reminder/db-unread-messages time-limit)]
    (->> res
         (map (juxt :user-id (comp tc/from-epoch :send-time)))
         (into #{}))))

(defn -m
  [time]
  (let [m (t/milli time)]
    (t/minus time (t/millis m))))

;; -------------------------
;;    SIMPLE LATE TESTS
;; -------------------------

(deftest unread-messages
  (clear-messages!)
  (let [user1-id   (user-service/create-user! project-ass1-id)
        user2-id   (user-service/create-user! project-ass1-id)
        user3-id   (user-service/create-user! project-ass1-id)
        message1-1 (messages/create-message-placeholder user1-id)
        message1-2 (messages/create-message-placeholder user1-id)
        message2-1 (messages/create-message-placeholder user2-id)
        message2-2 (messages/create-message-placeholder user2-id)
        message3-1 (messages/create-message-placeholder user3-id)
        message3-2 (messages/create-message-placeholder user3-id)
        t60days    (-m (t/minus (t/now) (t/days 60)))
        t45days    (-m (t/minus (t/now) (t/days 45)))
        now        (-m (t/now))]
    (set-message-properties message1-1 t60days false)
    (set-message-properties message1-2 now false)
    (set-message-properties message2-1 now true)
    (set-message-properties message2-2 t60days false)
    (set-message-properties message3-1 now false)
    (set-message-properties message3-2 t45days false)
    (is (= now (tc/from-epoch (utils/to-unix now))))
    (is (= #{[user1-id now]
             [user3-id t45days]}
           (check-messages t60days)))))