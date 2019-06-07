(ns bass4.test.assessment-reminder-activation
  (:require [clj-time.core :as t]
            [clojure.core.async :refer [chan alts!! timeout]]
            [bass4.assessment.reminder :as assessment-reminder]
            [bass4.test.assessment-utils :refer :all]
            [bass4.test.core :refer :all]
            [clojure.test :refer :all]
            [bass4.services.user :as user-service]
            [bass4.db.core :as db]
            [bass4.assessment.create-missing :as missing]
            [clojure.tools.logging :as log]))

(use-fixtures
  :once
  test-fixtures)

(use-fixtures
  :each
  random-date-tz-fixture)

;; -------------------------
;;  SIMPLE ACTIVATION TESTS
;; -------------------------

(deftest activation-group
  (let [group1-id (create-group!)
        user1-id  (user-service/create-user! project-id {:group group1-id})
        user2-id  (user-service/create-user! project-id {:group group1-id})
        group2-id (create-group!)
        user3-id  (user-service/create-user! project-id {:group group2-id})
        user4-id  (user-service/create-user! project-id {:group group2-id})]
    ; Today
    (create-group-administration!
      group1-id ass-G-s-2-3-p0 1 {:date (midnight *now*)})
    (create-group-administration!
      group1-id ass-G-week-e+s-3-4-p10 4 {:date (midnight *now*)})
    ; Wrong scope
    (create-group-administration!
      group1-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
    ; Tomorrow
    (create-group-administration!
      group1-id ass-G-week-e+s-3-4-p10 1 {:date (+ (midnight+d 1 *now*))})
    (create-group-administration!
      group2-id ass-G-s-2-3-p0 1 {:date (midnight *now*)})
    (create-group-administration!
      group2-id ass-G-week-e+s-3-4-p10 3 {:date (midnight *now*)})
    (is (= #{[user1-id false ass-G-s-2-3-p0 1 ::assessment-reminder/activation nil]
             [user1-id false ass-G-week-e+s-3-4-p10 4 ::assessment-reminder/activation nil]
             [user2-id false ass-G-s-2-3-p0 1 ::assessment-reminder/activation nil]
             [user2-id false ass-G-week-e+s-3-4-p10 4 ::assessment-reminder/activation nil]
             [user3-id false ass-G-s-2-3-p0 1 ::assessment-reminder/activation nil]
             [user3-id false ass-G-week-e+s-3-4-p10 3 ::assessment-reminder/activation nil]
             [user4-id false ass-G-s-2-3-p0 1 ::assessment-reminder/activation nil]
             [user4-id false ass-G-week-e+s-3-4-p10 3 ::assessment-reminder/activation nil]}
           (reminders *now*)))))

(deftest activation-group-individual-inactive
  (let [group1-id (create-group!)
        user1-id  (user-service/create-user! project-id {:group group1-id})
        user2-id  (user-service/create-user! project-id {:group group1-id})
        group2-id (create-group!)
        user3-id  (user-service/create-user! project-id {:group group2-id})
        user4-id  (user-service/create-user! project-id {:group group2-id})]
    (create-group-administration!
      group1-id ass-G-s-2-3-p0 1 {:date (midnight *now*)})
    (create-group-administration!
      group1-id ass-G-week-e+s-3-4-p10 4 {:date (midnight *now*)})
    (create-group-administration!
      group2-id ass-G-s-2-3-p0 1 {:date (midnight *now*)})
    (create-group-administration!
      group2-id ass-G-week-e+s-3-4-p10 3 {:date (midnight *now*)})

    ;; Inactivate for two users
    (create-participant-administration!
      user1-id ass-G-week-e+s-3-4-p10 4 {:active 0})
    (create-participant-administration!
      user4-id ass-G-s-2-3-p0 1 {:active 0})
    (is (= #{[user1-id false ass-G-s-2-3-p0 1 ::assessment-reminder/activation nil]
             [user2-id false ass-G-s-2-3-p0 1 ::assessment-reminder/activation nil]
             [user2-id false ass-G-week-e+s-3-4-p10 4 ::assessment-reminder/activation nil]
             [user3-id false ass-G-s-2-3-p0 1 ::assessment-reminder/activation nil]
             [user3-id false ass-G-week-e+s-3-4-p10 3 ::assessment-reminder/activation nil]
             [user4-id false ass-G-week-e+s-3-4-p10 3 ::assessment-reminder/activation nil]}
           (reminders *now*)))))

(deftest activation-individual
  (let [user1-id (user-service/create-user! project-id)
        user2-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user1-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
    (create-participant-administration!
      user2-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
    (create-participant-administration!
      user1-id ass-I-manual-s-5-10-q 3 {:date (midnight *now*)})
    (create-participant-administration!
      user2-id ass-I-manual-s-5-10-q 4 {:date (midnight *now*)})
    ; No reminders
    (create-participant-administration!
      user1-id ass-I-week-noremind 3 {:date (midnight *now*)})
    (create-participant-administration!
      user2-id ass-I-week-noremind 4 {:date (midnight *now*)})
    ; Wrong scope
    (create-participant-administration!
      user1-id ass-G-s-2-3-p0 1 {:date (midnight *now*)})
    ; Tomorrow
    (create-participant-administration!
      user1-id ass-I-week-noremind 1 {:date (+ (midnight+d 1 *now*))})
    (is (= #{[user1-id true ass-I-s-0-p100-message 1 ::assessment-reminder/activation nil]
             [user1-id true ass-I-manual-s-5-10-q 3 ::assessment-reminder/activation nil]
             [user2-id true ass-I-s-0-p100-message 1 ::assessment-reminder/activation nil]
             [user2-id true ass-I-manual-s-5-10-q 4 ::assessment-reminder/activation nil]}
           (reminders *now*)))))

(deftest activation-individual-group-inactive
  (let [group-id (create-group!)
        user1-id (user-service/create-user! project-id {:group group-id})
        user2-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user1-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
    (create-participant-administration!
      user2-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
    (create-participant-administration!
      user1-id ass-I-manual-s-5-10-q 3 {:date (midnight *now*)})
    (create-participant-administration!
      user2-id ass-I-manual-s-5-10-q 3 {:date (midnight *now*)})
    ;; Inactivate manual 3 at group level
    (create-group-administration!
      group-id ass-I-manual-s-5-10-q 3 {:active 0})
    (is (= #{[user1-id true ass-I-s-0-p100-message 1 ::assessment-reminder/activation nil]
             [user2-id true ass-I-s-0-p100-message 1 ::assessment-reminder/activation nil]
             [user2-id true ass-I-manual-s-5-10-q 3 ::assessment-reminder/activation nil]}
           (reminders *now*)))))

;;
;; No need to test clinician assessments because they cannot have reminders
;;

(deftest activation-start-hour
  (let [user-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user-id ass-I-hour8-2-20 1 {:date (midnight *now*)})
    (let [hour0 (midnight-joda *now*)]
      (is (= #{} (reminders hour0))))
    (let [hour7 (t/plus (midnight-joda *now*) (t/hours 7))]
      (is (= #{} (reminders hour7))))
    (let [hour8 (t/plus (midnight-joda *now*) (t/hours 8))]
      (is (= #{[user-id true ass-I-hour8-2-20 1 ::assessment-reminder/activation nil]}
             (reminders hour8))))))

;; --------------------------
;;  REMIND! ACTIVATION TESTS
;; --------------------------

(deftest activation-group-remind!
  (let [group1-id (create-group!)
        _         (user-service/create-user! project-id {:group group1-id})
        _         (user-service/create-user! project-id {:group group1-id})
        group2-id (create-group!)
        _         (user-service/create-user! project-id {:group group2-id})
        _         (user-service/create-user! project-id {:group group2-id})]
    ; Today
    (create-group-administration!
      group1-id ass-G-s-2-3-p0 1 {:date (midnight *now*)})
    (create-group-administration!
      group1-id ass-G-week-e+s-3-4-p10 4 {:date (midnight *now*)})
    (create-group-administration!
      group2-id ass-G-s-2-3-p0 1 {:date (midnight *now*)})
    (create-group-administration!
      group2-id ass-G-week-e+s-3-4-p10 3 {:date (midnight *now*)})
    (is (= 8 (remind!-created *now*)))))