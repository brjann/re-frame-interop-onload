(ns bass4.test.assessment-reminder-late
  (:require [clj-time.core :as t]
            [bass4.assessment.reminder :as assessment-reminder]
            [bass4.test.assessment-utils :refer :all]
            [bass4.test.core :refer :all]
            [clojure.test :refer :all]
            [bass4.services.user :as user-service]))

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
      group1-id ass-group-single-2-3 1 {:date (midnight+d -2 *now*)})
    (create-group-administration!
      group1-id ass-group-weekly-3-4 2 {:date (midnight+d -3 *now*)})
    (create-group-administration!
      group2-id ass-group-single-2-3 1 {:date (midnight+d -2 *now*)})
    (create-group-administration!
      group2-id ass-group-weekly-3-4 4 {:date (midnight+d -3 *now*)})
    (is (= #{[user1-id false ass-group-single-2-3 1 ::assessment-reminder/late]
             [user1-id false ass-group-weekly-3-4 2 ::assessment-reminder/late]
             [user2-id false ass-group-single-2-3 1 ::assessment-reminder/late]
             [user2-id false ass-group-weekly-3-4 4 ::assessment-reminder/late]}
           (reminders *now*)))))

(deftest late-participant
  (let [user1-id (user-service/create-user! project-id)
        user2-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user1-id ass-individual-manual-5-10 2 {:date (midnight+d -6 *now*)})
    ; Hour does not matter when late
    (create-participant-administration!
      user1-id ass-hour8-2-20 1 {:date (midnight+d -20 *now*)})
    (create-participant-administration!
      user2-id ass-individual-manual-5-10 4 {:date (midnight+d -6 *now*)})
    ;; No remind
    (create-participant-administration!
      user1-id ass-individual-weekly-no-remind 1 {:date (midnight+d -11 *now*)})
    ;; Activation but no late
    (create-participant-administration!
      user1-id ass-individual-single-0 1 {:date (midnight+d -1 *now*)})
    (is (= #{[user1-id true ass-individual-manual-5-10 2 ::assessment-reminder/late]
             [user1-id true ass-hour8-2-20 1 ::assessment-reminder/late]
             [user2-id true ass-individual-manual-5-10 4 ::assessment-reminder/late]}
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
      group1-id ass-group-single-2-3 1 {:date (midnight+d -1 *now*)})
    (create-group-administration!
      group2-id ass-group-single-2-3 1 {:date (midnight+d -2 *now*)})
    (create-group-administration!
      group3-id ass-group-single-2-3 1 {:date (midnight+d -6 *now*)})
    (create-group-administration!
      group4-id ass-group-single-2-3 1 {:date (midnight+d -7 *now*)})
    ;; User 3 has administration
    (create-participant-administration!
      user3-id ass-group-single-2-3 1)
    (is (= #{[user2-id false ass-group-single-2-3 1 ::assessment-reminder/late]
             [user3-id true ass-group-single-2-3 1 ::assessment-reminder/late]}
           (reminders *now*)))))

(deftest late-individual-boundaries
  (let [user1-id (user-service/create-user! project-id)
        user2-id (user-service/create-user! project-id)
        user3-id (user-service/create-user! project-id)
        user4-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user1-id ass-individual-manual-5-10 1 {:date (midnight+d -4 *now*)})
    (create-participant-administration!
      user2-id ass-individual-manual-5-10 2 {:date (midnight+d -5 *now*)})
    (create-participant-administration!
      user3-id ass-individual-manual-5-10 3 {:date (midnight+d -50 *now*)})
    (create-participant-administration!
      user4-id ass-individual-manual-5-10 4 {:date (midnight+d -51 *now*)})
    (is (= #{[user2-id true ass-individual-manual-5-10 2 ::assessment-reminder/late]
             [user3-id true ass-individual-manual-5-10 3 ::assessment-reminder/late]}
           (reminders *now*)))))

(deftest late-group-participant-inactive
  (let [group1-id (create-group!)
        group2-id (create-group!)
        user1-id  (user-service/create-user! project-id {:group group1-id})
        user2-id  (user-service/create-user! project-id {:group group2-id})]
    (create-group-administration!
      group1-id ass-group-single-2-3 1 {:date (midnight+d -2 *now*)})
    (create-group-administration!
      group2-id ass-group-single-2-3 1 {:date (midnight+d -2 *now*)})
    (create-participant-administration!
      user1-id ass-group-single-2-3 1 {:active 0})
    (is (= #{[user2-id false ass-group-single-2-3 1 ::assessment-reminder/late]}
           (reminders *now*)))))

(deftest late-participant-group-inactive
  (let [group1-id (create-group!)
        group2-id (create-group!)
        user1-id  (user-service/create-user! project-id {:group group1-id})
        user2-id  (user-service/create-user! project-id {:group group2-id})]
    (create-participant-administration!
      user1-id ass-individual-manual-5-10 2 {:date (midnight+d -6 *now*)})
    (create-participant-administration!
      user2-id ass-individual-manual-5-10 4 {:date (midnight+d -6 *now*)})
    (create-group-administration!
      group1-id ass-individual-manual-5-10 4 {:active 0})
    (is (= #{[user2-id true ass-individual-manual-5-10 4 ::assessment-reminder/late]}
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
      user1-id ass-individual-manual-5-10 2 {:date (midnight+d -6 *now*)})
    (create-group-administration!
      group1-id ass-group-single-2-3 1 {:date (midnight+d -2 *now*)})
    (create-group-administration!
      group1-id ass-group-weekly-3-4 2 {:date (midnight+d -3 *now*)})
    (create-group-administration!
      group2-id ass-group-single-2-3 1 {:date (midnight+d -2 *now*)})

    ;; ACTIVATION
    (create-group-administration!
      group1x-id ass-group-single-2-3 1 {:date (midnight *now*)})
    (create-group-administration!
      group1x-id ass-group-weekly-3-4 4 {:date (midnight *now*)})
    (create-participant-administration!
      user1x-id ass-individual-single-0 1 {:date (midnight *now*)})
    (is (= #{[user1-id true ass-individual-manual-5-10 2 ::assessment-reminder/late]
             [user1-id false ass-group-single-2-3 1 ::assessment-reminder/late]
             [user1-id false ass-group-weekly-3-4 2 ::assessment-reminder/late]
             [user2-id false ass-group-single-2-3 1 ::assessment-reminder/late]
             [user1x-id true ass-individual-single-0 1 ::assessment-reminder/activation]
             [user1x-id false ass-group-single-2-3 1 ::assessment-reminder/activation]
             [user1x-id false ass-group-weekly-3-4 4 ::assessment-reminder/activation]
             [user2x-id false ass-group-single-2-3 1 ::assessment-reminder/activation]
             [user2x-id false ass-group-weekly-3-4 4 ::assessment-reminder/activation]}
           (reminders *now*)))))