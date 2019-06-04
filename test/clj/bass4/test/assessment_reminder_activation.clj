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
      group1-id ass-group-single-2-3 1 {:date (midnight *now*)})
    (create-group-administration!
      group1-id ass-group-weekly-3-4 4 {:date (midnight *now*)})
    ; Wrong scope
    (create-group-administration!
      group1-id ass-individual-single-0 1 {:date (midnight *now*)})
    ; Tomorrow
    (create-group-administration!
      group1-id ass-group-weekly-3-4 1 {:date (+ (midnight+d 1 *now*))})
    (create-group-administration!
      group2-id ass-group-single-2-3 1 {:date (midnight *now*)})
    (create-group-administration!
      group2-id ass-group-weekly-3-4 3 {:date (midnight *now*)})
    (is (= #{[user1-id false ass-group-single-2-3 1 ::assessment-reminder/activation]
             [user1-id false ass-group-weekly-3-4 4 ::assessment-reminder/activation]
             [user2-id false ass-group-single-2-3 1 ::assessment-reminder/activation]
             [user2-id false ass-group-weekly-3-4 4 ::assessment-reminder/activation]
             [user3-id false ass-group-single-2-3 1 ::assessment-reminder/activation]
             [user3-id false ass-group-weekly-3-4 3 ::assessment-reminder/activation]
             [user4-id false ass-group-single-2-3 1 ::assessment-reminder/activation]
             [user4-id false ass-group-weekly-3-4 3 ::assessment-reminder/activation]}
           (reminders *now*)))))

(deftest activation-group-individual-inactive
  (let [group1-id (create-group!)
        user1-id  (user-service/create-user! project-id {:group group1-id})
        user2-id  (user-service/create-user! project-id {:group group1-id})
        group2-id (create-group!)
        user3-id  (user-service/create-user! project-id {:group group2-id})
        user4-id  (user-service/create-user! project-id {:group group2-id})]
    (create-group-administration!
      group1-id ass-group-single-2-3 1 {:date (midnight *now*)})
    (create-group-administration!
      group1-id ass-group-weekly-3-4 4 {:date (midnight *now*)})
    (create-group-administration!
      group2-id ass-group-single-2-3 1 {:date (midnight *now*)})
    (create-group-administration!
      group2-id ass-group-weekly-3-4 3 {:date (midnight *now*)})

    ;; Inactivate for two users
    (create-participant-administration!
      user1-id ass-group-weekly-3-4 4 {:active 0})
    (create-participant-administration!
      user4-id ass-group-single-2-3 1 {:active 0})
    (is (= #{[user1-id false ass-group-single-2-3 1 ::assessment-reminder/activation]
             [user2-id false ass-group-single-2-3 1 ::assessment-reminder/activation]
             [user2-id false ass-group-weekly-3-4 4 ::assessment-reminder/activation]
             [user3-id false ass-group-single-2-3 1 ::assessment-reminder/activation]
             [user3-id false ass-group-weekly-3-4 3 ::assessment-reminder/activation]
             [user4-id false ass-group-weekly-3-4 3 ::assessment-reminder/activation]}
           (reminders *now*)))))

(deftest activation-individual
  (let [user1-id (user-service/create-user! project-id)
        user2-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user1-id ass-individual-single-0 1 {:date (midnight *now*)})
    (create-participant-administration!
      user2-id ass-individual-single-0 1 {:date (midnight *now*)})
    (create-participant-administration!
      user1-id ass-individual-manual-5-10 3 {:date (midnight *now*)})
    (create-participant-administration!
      user2-id ass-individual-manual-5-10 4 {:date (midnight *now*)})
    ; No reminders
    (create-participant-administration!
      user1-id ass-individual-weekly-no-remind 3 {:date (midnight *now*)})
    (create-participant-administration!
      user2-id ass-individual-weekly-no-remind 4 {:date (midnight *now*)})
    ; Wrong scope
    (create-participant-administration!
      user1-id ass-group-single-2-3 1 {:date (midnight *now*)})
    ; Tomorrow
    (create-participant-administration!
      user1-id ass-individual-weekly-no-remind 1 {:date (+ (midnight+d 1 *now*))})
    (is (= #{[user1-id true ass-individual-single-0 1 ::assessment-reminder/activation]
             [user1-id true ass-individual-manual-5-10 3 ::assessment-reminder/activation]
             [user2-id true ass-individual-single-0 1 ::assessment-reminder/activation]
             [user2-id true ass-individual-manual-5-10 4 ::assessment-reminder/activation]}
           (reminders *now*)))))

(deftest activation-individual-group-inactive
  (let [group-id (create-group!)
        user1-id (user-service/create-user! project-id {:group group-id})
        user2-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user1-id ass-individual-single-0 1 {:date (midnight *now*)})
    (create-participant-administration!
      user2-id ass-individual-single-0 1 {:date (midnight *now*)})
    (create-participant-administration!
      user1-id ass-individual-manual-5-10 3 {:date (midnight *now*)})
    (create-participant-administration!
      user2-id ass-individual-manual-5-10 3 {:date (midnight *now*)})
    ;; Inactivate manual 3 at group level
    (create-group-administration!
      group-id ass-individual-manual-5-10 3 {:active 0})
    (is (= #{[user1-id true ass-individual-single-0 1 ::assessment-reminder/activation]
             [user2-id true ass-individual-single-0 1 ::assessment-reminder/activation]
             [user2-id true ass-individual-manual-5-10 3 ::assessment-reminder/activation]}
           (reminders *now*)))))

;;
;; No need to test clinician assessments because they cannot have reminders
;;

(deftest activation-start-hour
  (let [user-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user-id ass-hour8-2-20 1 {:date (midnight *now*)})
    (let [hour0 (midnight-joda *now*)]
      (is (= #{} (reminders hour0))))
    (let [hour7 (t/plus (midnight-joda *now*) (t/hours 7))]
      (is (= #{} (reminders hour7))))
    (let [hour8 (t/plus (midnight-joda *now*) (t/hours 8))]
      (is (= #{[user-id true ass-hour8-2-20 1 ::assessment-reminder/activation]}
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
      group1-id ass-group-single-2-3 1 {:date (midnight *now*)})
    (create-group-administration!
      group1-id ass-group-weekly-3-4 4 {:date (midnight *now*)})
    (create-group-administration!
      group2-id ass-group-single-2-3 1 {:date (midnight *now*)})
    (create-group-administration!
      group2-id ass-group-weekly-3-4 3 {:date (midnight *now*)})
    (binding [missing/*create-count-chan* (chan)]
      (let [[create-count _] (alts!! [missing/*create-count-chan* (timeout 1000)])]
        (is (= 8 create-count))))))