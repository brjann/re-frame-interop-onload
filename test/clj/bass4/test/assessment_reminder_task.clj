(ns ^:eftest/synchronized
  bass4.test.assessment-reminder-task
  (:require [clj-time.core :as t]
            [bass4.assessment.reminder :as assessment-reminder]
            [bass4.test.assessment-utils :refer :all]
            [bass4.test.core :refer :all]
            [clojure.test :refer :all]
            [bass4.services.user :as user-service]
            [bass4.db.core :as db]
            [bass4.routes.quick-login :as quick-login]
            [clojure.java.jdbc :as jdbc]
            [bass4.task.runner :as task-runner]
            [bass4.config :as config]
            [bass4.external-messages.queue-tasks :as queue-tasks])
  (:import (org.joda.time.tz CachedDateTimeZone)))

(use-fixtures
  :once
  test-fixtures)

(use-fixtures
  :each
  random-date-tz-fixture-new
  filter-created-objects-fixture)

(deftest reminder+queue-tasks
  (binding [assessment-reminder/db-standard-messages       (constantly {:sms "{FIRSTNAME} {LASTNAME}" :email "{EMAIL} {URL}"})
            assessment-reminder/db-reminder-start-and-stop (constantly {:start-hour 0 :stop-hour 25})
            quick-login/quicklogin-id                      (constantly "xxx")]

    (jdbc/execute! db/*db* "TRUNCATE TABLE external_message_email")
    (jdbc/execute! db/*db* "TRUNCATE TABLE external_message_sms")
    (let [run-db-task!           (fn [task]
                                   (let [f @#'task-runner/run-db-task!]
                                     (f db/*db*
                                        *now*
                                        (config/env :test-db)
                                        {:name :test :timezone (.getID ^CachedDateTimeZone *tz*)}
                                        task
                                        (subs (str task) 2)
                                        nil)))
          cycles                 (fn [exec-id] (-> (jdbc/query db/db-common ["SELECT cycles FROM common_log_tasks WHERE ExecId = ?" exec-id])
                                                   (first)
                                                   (:cycles)))

          group1-id              (create-group!)
          user1-id               (user-service/create-user! project-ass1-id {:group      group1-id
                                                                             :email      "user1@example.com"
                                                                             "SMSNumber" "111"
                                                                             "FirstName" "First1"
                                                                             "LastName"  "Last1"})
          _                      (user-service/create-user! project-ass1-id {:group      group1-id
                                                                             :email      "user2@example.com"
                                                                             "SMSNumber" "222"
                                                                             "FirstName" "First2"
                                                                             "LastName"  "Last2"})
          group2-id              (create-group!)
          _                      (user-service/create-user! project-ass1-id {:group      group2-id
                                                                             :email      "user3@example.com"
                                                                             "SMSNumber" "222"
                                                                             "FirstName" "First3"
                                                                             "LastName"  "Last3"})
          ass-I-manual-s-5-10-q  (create-assessment! {"Scope"                      0
                                                      "SendSMSWhenActivated"       1
                                                      "RemindParticipantsWhenLate" 1
                                                      "RemindInterval"             5
                                                      "MaxRemindCount"             10
                                                      "UseStandardMessage"         0
                                                      "RepetitionType"             2
                                                      "Repetitions"                4})
          ass-G-s-2-3-p0         (create-assessment! {"Scope"                      1
                                                      "SendSMSWhenActivated"       1
                                                      "RemindParticipantsWhenLate" 1
                                                      "RemindInterval"             2
                                                      "MaxRemindCount"             3})
          ass-G-week-e+s-3-4-p10 (create-assessment! {"Scope"                      1
                                                      "SendEmailWhenActivated"     1
                                                      "SendSMSWhenActivated"       1
                                                      "RemindParticipantsWhenLate" 1
                                                      "RemindInterval"             3
                                                      "MaxRemindCount"             4
                                                      "RepetitionType"             3
                                                      "Repetitions"                4
                                                      "CustomRepetitionInterval"   7})]

      ;; LATE
      (create-participant-administration!
        user1-id ass-I-manual-s-5-10-q 2 {:date (midnight+d -6 *now*)})
      (create-group-administration!
        group1-id ass-G-s-2-3-p0 1 {:date (midnight+d -2 *now*)})
      (create-group-administration!
        group1-id ass-G-week-e+s-3-4-p10 2 {:date (midnight+d -3 *now*)})

      ;; ACTIVATION
      (create-group-administration!
        group2-id ass-G-week-e+s-3-4-p10 1 {:date (midnight *now*)})
      (is (= 6 (cycles (run-db-task! #'assessment-reminder/reminder-task))))
      (is (= 3 (cycles (run-db-task! #'queue-tasks/email-task))))
      (is (= 3 (cycles (run-db-task! #'queue-tasks/sms-task))))
      (is (= 0 (cycles (run-db-task! #'assessment-reminder/reminder-task))))
      (is (= 0 (cycles (run-db-task! #'queue-tasks/email-task))))
      (is (= 0 (cycles (run-db-task! #'queue-tasks/sms-task)))))))

(deftest reminder-task-start-hour
  (binding [assessment-reminder/db-reminder-start-and-stop (constantly {:start-hour 8 :stop-hour 20})]
    (let [reminder-task          (fn [now]
                                   (:cycles (assessment-reminder/reminder-task
                                              db/*db*
                                              {:name :test :timezone (.getID ^CachedDateTimeZone *tz*)}
                                              now)))
          group1-id              (create-group!)
          ass-G-s-2-3-p0         (create-assessment! {"Scope"                      1
                                                      "SendSMSWhenActivated"       1
                                                      "RemindParticipantsWhenLate" 1
                                                      "RemindInterval"             2
                                                      "MaxRemindCount"             3})
          ass-G-week-e+s-3-4-p10 (create-assessment! {"Scope"                      1
                                                      "SendEmailWhenActivated"     1
                                                      "SendSMSWhenActivated"       1
                                                      "RemindParticipantsWhenLate" 1
                                                      "RemindInterval"             3
                                                      "MaxRemindCount"             4
                                                      "RepetitionType"             3
                                                      "Repetitions"                4
                                                      "CustomRepetitionInterval"   7})]
      (user-service/create-user! project-ass1-id {:group      group1-id
                                                  :email      "user1@example.com"
                                                  "SMSNumber" "111"})

      ;; LATE
      (create-group-administration!
        group1-id ass-G-s-2-3-p0 1 {:date (midnight+d -2 *now*)})
      (create-group-administration!
        group1-id ass-G-week-e+s-3-4-p10 2 {:date (midnight+d -3 *now*)})

      ;; ACTIVATION
      (let [now (t/plus (midnight-joda *now*) (t/hours 7))]
        (is (= nil (reminder-task now))))
      (let [now (t/plus (midnight-joda *now*) (t/hours 20))]
        (is (= nil (reminder-task now))))
      (let [now (t/plus (midnight-joda *now*) (t/hours 8))]
        (is (= 2 (reminder-task now)))))))