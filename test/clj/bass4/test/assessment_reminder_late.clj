(ns bass4.test.assessment-reminder-late
  (:require [clj-time.core :as t]
            [bass4.assessment.reminder :as assessment-reminder]
            [bass4.test.assessment-utils :refer :all]
            [bass4.test.core :refer :all]
            [clojure.test :refer :all]
            [bass4.services.user :as user-service]
            [bass4.db.core :as db]
            [clojure.tools.logging :as log]
            [bass4.utils :as utils]
            [bass4.routes.quick-login :as quick-login]
            [clj-time.coerce :as tc]
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

;; -------------------------
;;    SIMPLE LATE TESTS
;; -------------------------

(deftest late-group
  (let [group1-id              (create-group!)
        group2-id              (create-group!)
        user1-id               (user-service/create-user! project-ass1-id {:group group1-id})
        user2-id               (user-service/create-user! project-ass1-id {:group group2-id})
        ass-G-s-2-3-p0         (create-assessment! {"Scope"                        1
                                                    "SendSMSWhenActivated"         1
                                                    "RemindParticipantsWhenLate"   1
                                                    "RemindInterval"               2
                                                    "MaxRemindCount"               3
                                                    "CompetingAssessmentsPriority" 0})
        ass-G-week-e+s-3-4-p10 (create-assessment! {"Scope"                        1
                                                    "SendEmailWhenActivated"       1
                                                    "RemindParticipantsWhenLate"   1
                                                    "RemindInterval"               3
                                                    "MaxRemindCount"               4
                                                    "CompetingAssessmentsPriority" 10
                                                    "RepetitionType"               3
                                                    "Repetitions"                  4
                                                    "CustomRepetitionInterval"     7})]
    (create-group-administration!
      group1-id ass-G-s-2-3-p0 1 {:date (midnight+d -2 *now*)})
    (create-group-administration!
      group1-id ass-G-week-e+s-3-4-p10 2 {:date (midnight+d -3 *now*)})
    (create-group-administration!
      group2-id ass-G-s-2-3-p0 1 {:date (midnight+d -2 *now*)})
    (create-group-administration!
      group2-id ass-G-week-e+s-3-4-p10 4 {:date (midnight+d -3 *now*)})
    (is (= #{[user1-id false ass-G-s-2-3-p0 1 ::assessment-reminder/late 1]
             [user1-id false ass-G-week-e+s-3-4-p10 2 ::assessment-reminder/late 1]
             [user2-id false ass-G-s-2-3-p0 1 ::assessment-reminder/late 1]
             [user2-id false ass-G-week-e+s-3-4-p10 4 ::assessment-reminder/late 1]}
           (reminders *now*)))))

(deftest late-participant
  (let [user1-id               (user-service/create-user! project-ass1-id)
        user2-id               (user-service/create-user! project-ass1-id)
        ass-I-manual-s-5-10-q  (create-assessment! {"Scope"                        0
                                                    "SendSMSWhenActivated"         1
                                                    "RemindParticipantsWhenLate"   1
                                                    "RemindInterval"               5
                                                    "MaxRemindCount"               10
                                                    "CompetingAssessmentsPriority" 10
                                                    "RepetitionType"               2
                                                    "Repetitions"                  4})
        ass-I-week-noremind    (create-assessment! {"Scope"                      0
                                                    "RemindParticipantsWhenLate" 1
                                                    "RemindInterval"             5
                                                    "MaxRemindCount"             10})
        ass-I-s-0-p100-message (create-assessment! {"Scope"                        0
                                                    "SendSMSWhenActivated"         1
                                                    "CompetingAssessmentsPriority" 100})
        ass-I-hour8-2-20       (create-assessment! {"Scope"                      0
                                                    "SendEmailWhenActivated"     1
                                                    "ActivationHour"             8
                                                    "RemindParticipantsWhenLate" 1
                                                    "RemindInterval"             2
                                                    "MaxRemindCount"             20})]
    (create-participant-administration!
      user1-id ass-I-manual-s-5-10-q 2 {:date (midnight+d -6 *now*)})
    ; Hour does not matter when late
    (create-participant-administration!
      user1-id ass-I-hour8-2-20 1 {:date (midnight+d -20 *now*)})
    (create-participant-administration!
      user2-id ass-I-manual-s-5-10-q 4 {:date (midnight+d -6 *now*)})
    ;; No remind
    (create-participant-administration!
      user1-id ass-I-week-noremind 1 {:date (midnight+d -11 *now*)})
    ;; Activation but no late
    (create-participant-administration!
      user1-id ass-I-s-0-p100-message 1 {:date (midnight+d -1 *now*)})
    (is (= #{[user1-id true ass-I-manual-s-5-10-q 2 ::assessment-reminder/late 1]
             [user1-id true ass-I-hour8-2-20 1 ::assessment-reminder/late 10]
             [user2-id true ass-I-manual-s-5-10-q 4 ::assessment-reminder/late 1]}
           (reminders *now*)))))

(deftest late-group-boundaries
  (let [group1-id      (create-group!)
        group2-id      (create-group!)
        group3-id      (create-group!)
        group4-id      (create-group!)
        _              (user-service/create-user! project-ass1-id {:group group1-id})
        user2-id       (user-service/create-user! project-ass1-id {:group group2-id})
        user3-id       (user-service/create-user! project-ass1-id {:group group3-id})
        _              (user-service/create-user! project-ass1-id {:group group4-id})
        ass-G-s-2-3-p0 (create-assessment! {"Scope"                        1
                                            "SendSMSWhenActivated"         1
                                            "RemindParticipantsWhenLate"   1
                                            "RemindInterval"               2
                                            "MaxRemindCount"               3
                                            "CompetingAssessmentsPriority" 0})]
    (create-group-administration!
      group1-id ass-G-s-2-3-p0 1 {:date (midnight+d -1 *now*)})
    (create-group-administration!
      group2-id ass-G-s-2-3-p0 1 {:date (midnight+d -2 *now*)})
    (create-group-administration!
      group3-id ass-G-s-2-3-p0 1 {:date (midnight+d -6 *now*)})
    (create-group-administration!
      group4-id ass-G-s-2-3-p0 1 {:date (midnight+d -7 *now*)})
    ;; User 3 has administration
    (create-participant-administration!
      user3-id ass-G-s-2-3-p0 1)
    (is (= #{[user2-id false ass-G-s-2-3-p0 1 ::assessment-reminder/late 1]
             [user3-id true ass-G-s-2-3-p0 1 ::assessment-reminder/late 3]}
           (reminders *now*)))))

(deftest late-individual-boundaries
  (let [user1-id              (user-service/create-user! project-ass1-id)
        user2-id              (user-service/create-user! project-ass1-id)
        user3-id              (user-service/create-user! project-ass1-id)
        user4-id              (user-service/create-user! project-ass1-id)
        ass-I-manual-s-5-10-q (create-assessment! {"Scope"                        0
                                                   "SendSMSWhenActivated"         1
                                                   "RemindParticipantsWhenLate"   1
                                                   "RemindInterval"               5
                                                   "MaxRemindCount"               10
                                                   "CompetingAssessmentsPriority" 10
                                                   "RepetitionType"               2
                                                   "Repetitions"                  4})]
    (create-participant-administration!
      user1-id ass-I-manual-s-5-10-q 1 {:date (midnight+d -4 *now*)})
    (create-participant-administration!
      user2-id ass-I-manual-s-5-10-q 2 {:date (midnight+d -5 *now*)})
    (create-participant-administration!
      user3-id ass-I-manual-s-5-10-q 3 {:date (midnight+d -50 *now*)})
    (create-participant-administration!
      user4-id ass-I-manual-s-5-10-q 4 {:date (midnight+d -51 *now*)})
    ;; This occasionally fails (-51 is included) and therefore added logging
    (let [reminders' (reminders *now*)
          expected   #{[user2-id true ass-I-manual-s-5-10-q 2 ::assessment-reminder/late 1]
                       [user3-id true ass-I-manual-s-5-10-q 3 ::assessment-reminder/late 10]}]
      (is (= expected reminders'))
      (when-not (= expected reminders')
        (log/error "Time was " *now* " and timezone was " *tz*)))))

(deftest late-group-participant-inactive
  (let [group1-id      (create-group!)
        group2-id      (create-group!)
        user1-id       (user-service/create-user! project-ass1-id {:group group1-id})
        user2-id       (user-service/create-user! project-ass1-id {:group group2-id})
        ass-G-s-2-3-p0 (create-assessment! {"Scope"                        1
                                            "SendSMSWhenActivated"         1
                                            "RemindParticipantsWhenLate"   1
                                            "RemindInterval"               2
                                            "MaxRemindCount"               3
                                            "CompetingAssessmentsPriority" 0})]
    (create-group-administration!
      group1-id ass-G-s-2-3-p0 1 {:date (midnight+d -2 *now*)})
    (create-group-administration!
      group2-id ass-G-s-2-3-p0 1 {:date (midnight+d -2 *now*)})
    (create-participant-administration!
      user1-id ass-G-s-2-3-p0 1 {:active 0})
    (is (= #{[user2-id false ass-G-s-2-3-p0 1 ::assessment-reminder/late 1]}
           (reminders *now*)))))

(deftest late-participant-group-inactive
  (let [group1-id             (create-group!)
        group2-id             (create-group!)
        user1-id              (user-service/create-user! project-ass1-id {:group group1-id})
        user2-id              (user-service/create-user! project-ass1-id {:group group2-id})
        ass-I-manual-s-5-10-q (create-assessment! {"Scope"                        0
                                                   "SendSMSWhenActivated"         1
                                                   "RemindParticipantsWhenLate"   1
                                                   "RemindInterval"               5
                                                   "MaxRemindCount"               10
                                                   "CompetingAssessmentsPriority" 10
                                                   "RepetitionType"               2
                                                   "Repetitions"                  4})]
    (create-participant-administration!
      user1-id ass-I-manual-s-5-10-q 2 {:date (midnight+d -6 *now*)})
    (create-participant-administration!
      user2-id ass-I-manual-s-5-10-q 4 {:date (midnight+d -6 *now*)})
    (create-group-administration!
      group1-id ass-I-manual-s-5-10-q 2 {:active 0})
    (is (= #{[user2-id true ass-I-manual-s-5-10-q 4 ::assessment-reminder/late 1]}
           (reminders *now*)))))

(deftest late+activation
  (let [group1-id              (create-group!)
        group2-id              (create-group!)
        user1-id               (user-service/create-user! project-ass1-id {:group group1-id})
        user2-id               (user-service/create-user! project-ass1-id {:group group2-id})
        group1x-id             (create-group!)
        user1x-id              (user-service/create-user! project-ass1-id {:group group1x-id})
        user2x-id              (user-service/create-user! project-ass1-id {:group group1x-id})
        ass-I-manual-s-5-10-q  (create-assessment! {"Scope"                        0
                                                    "SendSMSWhenActivated"         1
                                                    "RemindParticipantsWhenLate"   1
                                                    "RemindInterval"               5
                                                    "MaxRemindCount"               10
                                                    "CompetingAssessmentsPriority" 10
                                                    "RepetitionType"               2
                                                    "Repetitions"                  4})
        ass-G-s-2-3-p0         (create-assessment! {"Scope"                        1
                                                    "SendSMSWhenActivated"         1
                                                    "RemindParticipantsWhenLate"   1
                                                    "RemindInterval"               2
                                                    "MaxRemindCount"               3
                                                    "CompetingAssessmentsPriority" 0})
        ass-G-week-e+s-3-4-p10 (create-assessment! {"Scope"                        1
                                                    "SendEmailWhenActivated"       1
                                                    "RemindParticipantsWhenLate"   1
                                                    "RemindInterval"               3
                                                    "MaxRemindCount"               4
                                                    "CompetingAssessmentsPriority" 10
                                                    "RepetitionType"               3
                                                    "Repetitions"                  4
                                                    "CustomRepetitionInterval"     7})
        ass-I-s-0-p100-message (create-assessment! {"Scope"                        0
                                                    "SendSMSWhenActivated"         1
                                                    "CompetingAssessmentsPriority" 100})]

    ;; LATE
    (create-participant-administration!
      user1-id ass-I-manual-s-5-10-q 2 {:date (midnight+d -6 *now*)})
    (create-group-administration!
      group1-id ass-G-s-2-3-p0 1 {:date (midnight+d -2 *now*)})
    (create-group-administration!
      group1-id ass-G-week-e+s-3-4-p10 2 {:date (midnight+d -3 *now*)})
    (create-group-administration!
      group2-id ass-G-s-2-3-p0 1 {:date (midnight+d -2 *now*)})

    ;; ACTIVATION
    (create-group-administration!
      group1x-id ass-G-s-2-3-p0 1 {:date (midnight *now*)})
    (create-group-administration!
      group1x-id ass-G-week-e+s-3-4-p10 4 {:date (midnight *now*)})
    (create-participant-administration!
      user1x-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
    (is (= #{[user1-id true ass-I-manual-s-5-10-q 2 ::assessment-reminder/late 1]
             [user1-id false ass-G-s-2-3-p0 1 ::assessment-reminder/late 1]
             [user1-id false ass-G-week-e+s-3-4-p10 2 ::assessment-reminder/late 1]
             [user2-id false ass-G-s-2-3-p0 1 ::assessment-reminder/late 1]
             [user1x-id true ass-I-s-0-p100-message 1 ::assessment-reminder/activation nil]
             [user1x-id false ass-G-s-2-3-p0 1 ::assessment-reminder/activation nil]
             [user1x-id false ass-G-week-e+s-3-4-p10 4 ::assessment-reminder/activation nil]
             [user2x-id false ass-G-s-2-3-p0 1 ::assessment-reminder/activation nil]
             [user2x-id false ass-G-week-e+s-3-4-p10 4 ::assessment-reminder/activation nil]}
           (reminders *now*)))))

(deftest late+activation-email
  (let [group1-id              (create-group!)
        user1-id               (user-service/create-user! project-ass1-id {:group group1-id})
        user2-id               (user-service/create-user! project-ass1-id {:group group1-id})
        user3-id               (user-service/create-user! project-ass1-id {:group group1-id})
        ass-G-week-e+s-3-4-p10 (create-assessment! {"Scope"                      1
                                                    "SendEmailWhenActivated"     1
                                                    "RemindParticipantsWhenLate" 1
                                                    "RemindInterval"             3
                                                    "MaxRemindCount"             4
                                                    "RepetitionType"             3
                                                    "Repetitions"                4
                                                    "CustomRepetitionInterval"   7})
        ass-I-manual-s-5-10-q  (create-assessment! {"Scope"                 0
                                                    "SendSMSWhenActivated"  1
                                                    "CustomReminderMessage" "{QUICKURL}"})
        ass-G-s-2-3-p0         (create-assessment! {"Scope"                        1
                                                    "SendSMSWhenActivated"         1
                                                    "RemindParticipantsWhenLate"   1
                                                    "RemindInterval"               2
                                                    "MaxRemindCount"               3
                                                    "CompetingAssessmentsPriority" 0})
        ass-I-s-0-p100-message (create-assessment! {"Scope"                        0
                                                    "SendSMSWhenActivated"         1
                                                    "CompetingAssessmentsPriority" 100
                                                    "CustomReminderMessage"        "message"
                                                    "UseStandardMessage"           0})]

    (create-group-administration!
      group1-id ass-G-week-e+s-3-4-p10 2 {:date (midnight+d -3 *now*)})
    (create-group-administration!
      group1-id ass-G-s-2-3-p0 1 {:date (midnight+d -2 *now*)})

    (create-participant-administration!
      user1-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
    (create-participant-administration!
      user2-id ass-I-manual-s-5-10-q 1 {:date (midnight *now*)})

    (is (= #{[user3-id ass-G-week-e+s-3-4-p10 :email]
             [user3-id ass-G-s-2-3-p0 :sms]
             [user1-id ass-G-week-e+s-3-4-p10 :email]
             [user1-id ass-I-s-0-p100-message :sms]
             [user2-id ass-G-week-e+s-3-4-p10 :email]
             [user2-id ass-I-manual-s-5-10-q :sms]}
           (messages *now*)))))

(deftest late-group-remind!
  (let [group1-id              (create-group!)
        group2-id              (create-group!)
        _                      (user-service/create-user! project-ass1-id {:group group1-id})
        _                      (user-service/create-user! project-ass1-id {:group group2-id})
        ass-G-s-2-3-p0         (create-assessment! {"Scope"                        1
                                                    "SendSMSWhenActivated"         1
                                                    "RemindParticipantsWhenLate"   1
                                                    "RemindInterval"               2
                                                    "MaxRemindCount"               3
                                                    "CompetingAssessmentsPriority" 0})
        ass-G-week-e+s-3-4-p10 (create-assessment! {"Scope"                        1
                                                    "SendEmailWhenActivated"       1
                                                    "RemindParticipantsWhenLate"   1
                                                    "RemindInterval"               3
                                                    "MaxRemindCount"               4
                                                    "CompetingAssessmentsPriority" 10
                                                    "RepetitionType"               3
                                                    "Repetitions"                  4
                                                    "CustomRepetitionInterval"     7})]
    (create-group-administration!
      group1-id ass-G-s-2-3-p0 1 {:date (midnight+d -2 *now*)})
    (create-group-administration!
      group1-id ass-G-week-e+s-3-4-p10 2 {:date (midnight+d -3 *now*)})
    (create-group-administration!
      group2-id ass-G-s-2-3-p0 1 {:date (midnight+d -2 *now*)})
    (create-group-administration!
      group2-id ass-G-week-e+s-3-4-p10 4 {:date (midnight+d -3 *now*)})
    (is (= 4 (remind!-administrations-created *now*)))))

(deftest late+activation-reminders-sent!
  (let [group1-id              (create-group!)
        group2-id              (create-group!)
        user1-id               (user-service/create-user! project-ass1-id {:group group1-id})
        user2-id               (user-service/create-user! project-ass1-id {:group group2-id})
        group1x-id             (create-group!)
        user1x-id              (user-service/create-user! project-ass1-id {:group group1x-id})
        user2x-id              (user-service/create-user! project-ass1-id {:group group1x-id})
        ass-I-manual-s-5-10-q  (create-assessment! {"Scope"                        0
                                                    "SendSMSWhenActivated"         1
                                                    "RemindParticipantsWhenLate"   1
                                                    "RemindInterval"               5
                                                    "MaxRemindCount"               10
                                                    "CompetingAssessmentsPriority" 10
                                                    "RepetitionType"               2
                                                    "Repetitions"                  4})
        ass-G-s-2-3-p0         (create-assessment! {"Scope"                        1
                                                    "SendSMSWhenActivated"         1
                                                    "RemindParticipantsWhenLate"   1
                                                    "RemindInterval"               2
                                                    "MaxRemindCount"               3
                                                    "CompetingAssessmentsPriority" 0})
        ass-G-week-e+s-3-4-p10 (create-assessment! {"Scope"                        1
                                                    "SendEmailWhenActivated"       1
                                                    "RemindParticipantsWhenLate"   1
                                                    "RemindInterval"               3
                                                    "MaxRemindCount"               4
                                                    "CompetingAssessmentsPriority" 10
                                                    "RepetitionType"               3
                                                    "Repetitions"                  4
                                                    "CustomRepetitionInterval"     7})
        ass-I-s-0-p100-message (create-assessment! {"Scope"                        0
                                                    "SendSMSWhenActivated"         1
                                                    "CompetingAssessmentsPriority" 100})]

    ;; LATE
    (create-participant-administration!
      user1-id ass-I-manual-s-5-10-q 2 {:date (midnight+d -6 *now*)})
    (create-group-administration!
      group1-id ass-G-s-2-3-p0 1 {:date (midnight+d -2 *now*)})
    (create-group-administration!
      group1-id ass-G-week-e+s-3-4-p10 2 {:date (midnight+d -3 *now*)})
    (create-group-administration!
      group2-id ass-G-s-2-3-p0 1 {:date (midnight+d -2 *now*)})

    ;; ACTIVATION
    (create-group-administration!
      group1x-id ass-G-s-2-3-p0 1 {:date (midnight *now*)})
    (create-group-administration!
      group1x-id ass-G-week-e+s-3-4-p10 4 {:date (midnight *now*)})
    (create-participant-administration!
      user1x-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
    (is (= #{[user1-id true ass-I-manual-s-5-10-q 2 ::assessment-reminder/late 1]
             [user1-id false ass-G-s-2-3-p0 1 ::assessment-reminder/late 1]
             [user1-id false ass-G-week-e+s-3-4-p10 2 ::assessment-reminder/late 1]
             [user2-id false ass-G-s-2-3-p0 1 ::assessment-reminder/late 1]
             [user1x-id true ass-I-s-0-p100-message 1 ::assessment-reminder/activation nil]
             [user1x-id false ass-G-s-2-3-p0 1 ::assessment-reminder/activation nil]
             [user1x-id false ass-G-week-e+s-3-4-p10 4 ::assessment-reminder/activation nil]
             [user2x-id false ass-G-s-2-3-p0 1 ::assessment-reminder/activation nil]
             [user2x-id false ass-G-week-e+s-3-4-p10 4 ::assessment-reminder/activation nil]}
           (reminders *now*)))
    (remind! *now*)
    (is (= #{} (reminders *now*)))))

(deftest late-reminders-sent!-advance-time
  (let [group1-id      (create-group!)
        group2-id      (create-group!)
        group3-id      (create-group!)
        group4-id      (create-group!)
        user1-id       (user-service/create-user! project-ass1-id {:group group1-id})
        user2-id       (user-service/create-user! project-ass1-id {:group group2-id})
        user3-id       (user-service/create-user! project-ass1-id {:group group3-id})
        _              (user-service/create-user! project-ass1-id {:group group4-id})
        ass-G-s-2-3-p0 (create-assessment! {"Scope"                        1
                                            "SendSMSWhenActivated"         1
                                            "RemindParticipantsWhenLate"   1
                                            "RemindInterval"               2
                                            "MaxRemindCount"               3
                                            "CompetingAssessmentsPriority" 0})]
    (create-group-administration!
      group1-id ass-G-s-2-3-p0 1 {:date (midnight+d -1 *now*)})
    (create-group-administration!
      group2-id ass-G-s-2-3-p0 1 {:date (midnight+d -2 *now*)})
    (create-group-administration!
      group3-id ass-G-s-2-3-p0 1 {:date (midnight+d -6 *now*)})
    (create-group-administration!
      group4-id ass-G-s-2-3-p0 1 {:date (midnight+d -7 *now*)})
    ;; User 3 has administration
    (create-participant-administration!
      user3-id ass-G-s-2-3-p0 1)
    (is (= #{[user2-id false ass-G-s-2-3-p0 1 ::assessment-reminder/late 1]
             [user3-id true ass-G-s-2-3-p0 1 ::assessment-reminder/late 3]}
           (reminders *now*)))
    (is (= 1 (remind!-administrations-created *now*)))
    (is (= #{} (reminders *now*)))
    (let [now+ (t/plus *now* (t/days 1))]
      (is (= #{[user1-id false ass-G-s-2-3-p0 1 ::assessment-reminder/late 1]}
             (reminders now+)))
      (is (= 1 (remind!-administrations-created now+))))
    (let [now+ (t/plus *now* (t/days 3))]
      (is (= #{[user1-id true ass-G-s-2-3-p0 1 ::assessment-reminder/late 2]
               [user2-id true ass-G-s-2-3-p0 1 ::assessment-reminder/late 2]}
             (reminders now+)))
      (remind! now+)
      (is (= #{} (reminders now+))))
    (let [now+ (t/plus *now* (t/days 5))]
      (is (= #{[user1-id true ass-G-s-2-3-p0 1 ::assessment-reminder/late 3]}
             (reminders now+)))
      (remind! now+)
      (is (= #{} (reminders now+))))))

(deftest quick-login
  (binding [quick-login/db-quick-login-settings (constantly {:allowed? true :expiration-days 14})]
    (let [user1-id               (user-service/create-user! project-ass1-id)
          user2-id               (user-service/create-user! project-ass1-id {"QuickLoginPassword"  "xxx"
                                                                             "QuickLoginTimestamp" (utils/to-unix (t/minus *now* (t/days 7)))})
          user3-id               (user-service/create-user! project-ass1-id {"QuickLoginPassword"  "xxx"
                                                                             "QuickLoginTimestamp" (utils/to-unix (t/minus *now* (t/days 8)))})
          user4-id               (user-service/create-user! project-ass1-id)
          ass-I-manual-s-5-10-q  (create-assessment! {"Scope"                           0
                                                      "SendSMSWhenActivated"            1
                                                      "CreateNewQuickLoginOnActivation" 1
                                                      "CustomReminderMessage"           "{QUICKURL}"})
          ass-I-s-0-p100-message (create-assessment! {"Scope"                        0
                                                      "SendSMSWhenActivated"         1
                                                      "CompetingAssessmentsPriority" 100})]

      (create-participant-administration!
        user1-id ass-I-manual-s-5-10-q 1 {:date (midnight *now*)})
      (create-participant-administration!
        user2-id ass-I-manual-s-5-10-q 1 {:date (midnight *now*)})
      (create-participant-administration!
        user3-id ass-I-manual-s-5-10-q 1 {:date (midnight *now*)})
      (create-participant-administration!
        user4-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
      (is (= #{user1-id user3-id} (remind!-quick-logins-created *now*))))))

(deftest late+activation-messages
  (binding [assessment-reminder/db-standard-messages (constantly {:sms "{FIRSTNAME} {LASTNAME}" :email "{EMAIL} {URL}"})
            quick-login/quicklogin-id                (constantly "xxx")]
    (let [group1-id              (create-group!)
          user1-id               (user-service/create-user! project-ass1-id {:group      group1-id
                                                                             :email      "user1@example.com"
                                                                             "SMSNumber" "111"
                                                                             "FirstName" "First1"
                                                                             "LastName"  "Last1"})
          user2-id               (user-service/create-user! project-ass1-id {:group      group1-id
                                                                             :email      "user2@example.com"
                                                                             "SMSNumber" "222"
                                                                             "FirstName" "First2"
                                                                             "LastName"  "Last2"})
          group2-id              (create-group!)
          ;; 3 gets no sms because not valid number
          user3-id               (user-service/create-user! project-ass1-id {:group      group2-id
                                                                             :email      "user3@example.com"
                                                                             "SMSNumber" "xxx"
                                                                             "FirstName" "First3"
                                                                             "LastName"  "Last3"})
          ;; 4 gets no sms because no first or last name
          user4-id               (user-service/create-user! project-ass1-id {:group      group2-id
                                                                             :email      "user4@example.com"
                                                                             "SMSNumber" "444"})
          ;; 5 gets no email because not valid address
          user5-id               (user-service/create-user! project-ass1-id {:group      group2-id
                                                                             :email      "xxx"
                                                                             "SMSNumber" "555"
                                                                             "FirstName" "First5"
                                                                             "LastName"  "Last5"})
          ass-I-manual-s-5-10-q  (create-assessment! {"Scope"                           0
                                                      "CompetingAssessmentsPriority"    10
                                                      "SendSMSWhenActivated"            1
                                                      "RemindParticipantsWhenLate"      1
                                                      "RemindInterval"                  5
                                                      "MaxRemindCount"                  10
                                                      "CreateNewQuickLoginOnActivation" 1
                                                      "UseStandardMessage"              0
                                                      "RepetitionType"                  2
                                                      "Repetitions"                     4
                                                      "CustomReminderMessage"           "{QUICKURL}"})
          ass-G-s-2-3-p0         (create-assessment! {"Scope"                        1
                                                      "SendSMSWhenActivated"         1
                                                      "RemindParticipantsWhenLate"   1
                                                      "RemindInterval"               2
                                                      "MaxRemindCount"               3
                                                      "CompetingAssessmentsPriority" 0})
          ass-G-week-e+s-3-4-p10 (create-assessment! {"Scope"                        1
                                                      "SendEmailWhenActivated"       1
                                                      "SendSMSWhenActivated"         1
                                                      "RemindParticipantsWhenLate"   1
                                                      "RemindInterval"               3
                                                      "MaxRemindCount"               4
                                                      "CompetingAssessmentsPriority" 10
                                                      "RepetitionType"               3
                                                      "Repetitions"                  4
                                                      "CustomRepetitionInterval"     7})]

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
      (let [messages (remind!-messages-sent *now*)
            expected #{[user1-id :sms "111" "https://test.bass4.com/q/xxx"]
                       [user2-id :sms "222" "First2 Last2"]
                       [user1-id :email "user1@example.com" "Reminder" "user1@example.com https://test.bass4.com"]
                       [user2-id :email "user2@example.com" "Reminder" "user2@example.com https://test.bass4.com"]
                       [user3-id :email "user3@example.com" "Information" "user3@example.com https://test.bass4.com"]
                       [user4-id :email "user4@example.com" "Information" "user4@example.com https://test.bass4.com"]
                       [user5-id :sms "555" "First5 Last5"]}]
        (is (= expected
               messages))))))

(deftest late-tz-malta-dst
  (let [m (tc/from-string "2019-10-29T00:00:00.000Z")]      ;"1971-10-13T00:00:00.000Z"
    (binding [*now* (t/plus m (t/hours 22))
              *tz*  (t/time-zone-for-id "Europe/Malta")]
      (let [user3-id              (user-service/create-user! project-ass1-id)
            ass-I-manual-s-5-10-q (create-assessment! {"Scope"                           0
                                                       "CompetingAssessmentsPriority"    10
                                                       "SendSMSWhenActivated"            1
                                                       "RemindParticipantsWhenLate"      1
                                                       "RemindInterval"                  5
                                                       "MaxRemindCount"                  10
                                                       "CreateNewQuickLoginOnActivation" 1
                                                       "UseStandardMessage"              0
                                                       "RepetitionType"                  2
                                                       "Repetitions"                     4
                                                       "CustomReminderMessage"           "{QUICKURL}"})]
        (create-participant-administration!
          user3-id ass-I-manual-s-5-10-q 3 {:date (midnight+d -50 *now*)})
        (let [reminders' (reminders *now*)
              expected   #{[user3-id true ass-I-manual-s-5-10-q 3 ::assessment-reminder/late 10]}]
          (is (= expected reminders')))))))
