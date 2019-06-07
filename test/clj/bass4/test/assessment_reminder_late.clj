(ns bass4.test.assessment-reminder-late
  (:require [clj-time.core :as t]
            [bass4.assessment.reminder :as assessment-reminder]
            [bass4.test.assessment-utils :refer :all]
            [bass4.test.core :refer :all]
            [clojure.test :refer :all]
            [bass4.services.user :as user-service]
            [bass4.assessment.create-missing :as missing]
            [bass4.db.core :as db]
            [clojure.tools.logging :as log]
            [clojure.pprint :as pprint]))

(use-fixtures
  :once
  test-fixtures)

(use-fixtures
  :each
  random-date-tz-fixture)

;; -------------------------
;;    SIMPLE LATE TESTS
;; -------------------------

(deftest late-group
  (let [group1-id (create-group!)
        group2-id (create-group!)
        user1-id  (user-service/create-user! project-id {:group group1-id})
        user2-id  (user-service/create-user! project-id {:group group2-id})]
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
  (let [user1-id (user-service/create-user! project-id)
        user2-id (user-service/create-user! project-id)]
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
  (let [group1-id (create-group!)
        group2-id (create-group!)
        group3-id (create-group!)
        group4-id (create-group!)
        _         (user-service/create-user! project-id {:group group1-id})
        user2-id  (user-service/create-user! project-id {:group group2-id})
        user3-id  (user-service/create-user! project-id {:group group3-id})
        _         (user-service/create-user! project-id {:group group4-id})]
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
  (let [user1-id (user-service/create-user! project-id)
        user2-id (user-service/create-user! project-id)
        user3-id (user-service/create-user! project-id)
        user4-id (user-service/create-user! project-id)]
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
  (let [group1-id (create-group!)
        group2-id (create-group!)
        user1-id  (user-service/create-user! project-id {:group group1-id})
        user2-id  (user-service/create-user! project-id {:group group2-id})]
    (create-group-administration!
      group1-id ass-G-s-2-3-p0 1 {:date (midnight+d -2 *now*)})
    (create-group-administration!
      group2-id ass-G-s-2-3-p0 1 {:date (midnight+d -2 *now*)})
    (create-participant-administration!
      user1-id ass-G-s-2-3-p0 1 {:active 0})
    (is (= #{[user2-id false ass-G-s-2-3-p0 1 ::assessment-reminder/late 1]}
           (reminders *now*)))))

(deftest late-participant-group-inactive
  (let [group1-id (create-group!)
        group2-id (create-group!)
        user1-id  (user-service/create-user! project-id {:group group1-id})
        user2-id  (user-service/create-user! project-id {:group group2-id})]
    (create-participant-administration!
      user1-id ass-I-manual-s-5-10-q 2 {:date (midnight+d -6 *now*)})
    (create-participant-administration!
      user2-id ass-I-manual-s-5-10-q 4 {:date (midnight+d -6 *now*)})
    (create-group-administration!
      group1-id ass-I-manual-s-5-10-q 4 {:active 0})
    (is (= #{[user2-id true ass-I-manual-s-5-10-q 4 ::assessment-reminder/late 1]}
           (reminders *now*)))))

(deftest late+activation
  (let [group1-id  (create-group!)
        group2-id  (create-group!)
        user1-id   (user-service/create-user! project-id {:group group1-id})
        user2-id   (user-service/create-user! project-id {:group group2-id})
        group1x-id (create-group!)
        user1x-id  (user-service/create-user! project-id {:group group1x-id})
        user2x-id  (user-service/create-user! project-id {:group group1x-id})]

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
  (let [group1-id (create-group!)
        user1-id  (user-service/create-user! project-id {:group group1-id})
        user2-id  (user-service/create-user! project-id {:group group1-id})
        user3-id  (user-service/create-user! project-id {:group group1-id})]

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
  (let [group1-id (create-group!)
        group2-id (create-group!)
        _         (user-service/create-user! project-id {:group group1-id})
        _         (user-service/create-user! project-id {:group group2-id})]
    (create-group-administration!
      group1-id ass-G-s-2-3-p0 1 {:date (midnight+d -2 *now*)})
    (create-group-administration!
      group1-id ass-G-week-e+s-3-4-p10 2 {:date (midnight+d -3 *now*)})
    (create-group-administration!
      group2-id ass-G-s-2-3-p0 1 {:date (midnight+d -2 *now*)})
    (create-group-administration!
      group2-id ass-G-week-e+s-3-4-p10 4 {:date (midnight+d -3 *now*)})
    (is (= 4 (remind!-created *now*)))
    #_(binding [missing/*create-count-chan* (chan)]
        (remind! *now*)
        (let [[create-count _] (alts!! [missing/*create-count-chan* (timeout 1000)])]
          ))))

(deftest late+activation-reminders-sent!
  (let [group1-id  (create-group!)
        group2-id  (create-group!)
        user1-id   (user-service/create-user! project-id {:group group1-id})
        user2-id   (user-service/create-user! project-id {:group group2-id})
        group1x-id (create-group!)
        user1x-id  (user-service/create-user! project-id {:group group1x-id})
        user2x-id  (user-service/create-user! project-id {:group group1x-id})]

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
  (let [group1-id (create-group!)
        group2-id (create-group!)
        group3-id (create-group!)
        group4-id (create-group!)
        user1-id  (user-service/create-user! project-id {:group group1-id})
        user2-id  (user-service/create-user! project-id {:group group2-id})
        user3-id  (user-service/create-user! project-id {:group group3-id})
        _         (user-service/create-user! project-id {:group group4-id})]
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
    (is (= 1 (remind!-created *now*)))
    (is (= #{} (reminders *now*)))
    (let [now+ (t/plus *now* (t/days 1))]
      (is (= #{[user1-id false ass-G-s-2-3-p0 1 ::assessment-reminder/late 1]}
             (reminders now+)))
      (is (= 1 (remind!-created now+))))
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

