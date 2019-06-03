(ns bass4.test.assessment-reminder
  (:require [clj-time.core :as t]
            [bass4.db.core :refer [*db*] :as db]
            [bass4.assessment.reminder :as assessment-reminder]
            [bass4.test.assessment-utils :refer :all]
            [bass4.test.core :refer :all]
            [clojure.test :refer :all]
            [bass4.services.bass :as bass]
            [bass4.services.user :as user-service]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [clj-time.coerce :as tc]
            [clojure.pprint :as pprint]
            [bass4.utils :as utils]
            [bass4.assessment.ongoing :as assessment-ongoing]))

(use-fixtures
  :once
  test-fixtures)

(use-fixtures
  :each
  random-date-tz-fixture)

(defn activation-reminders
  [now]
  (->> (assessment-reminder/activation-reminders* db/*db* now *tz*)
       (map #(vector
               (:user-id %)
               (some? (:participant-administration-id %))
               (:assessment-id %)
               (:assessment-index %)
               (::assessment-reminder/remind-type %)))
       (into #{})))

(defn late-reminders
  [now]
  (->> (assessment-reminder/late-reminders* db/*db* now)
       (map #(vector
               (:user-id %)
               (some? (:participant-administration-id %))
               (:assessment-id %)
               (:assessment-index %)
               (::assessment-reminder/remind-type %)))
       (into #{})))

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
           (activation-reminders *now*)))))

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
           (activation-reminders *now*)))))

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
           (activation-reminders *now*)))))

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
           (activation-reminders *now*)))))

;;
;; No need to test clinician assessments because they cannot have reminders
;;

(deftest activation-start-hour
  (let [user-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user-id ass-hour8-2-20 1 {:date (midnight *now*)})
    (let [hour0 (midnight-joda *now*)]
      (is (= #{} (activation-reminders hour0))))
    (let [hour7 (t/plus (midnight-joda *now*) (t/hours 7))]
      (is (= #{} (activation-reminders hour7))))
    (let [hour8 (t/plus (midnight-joda *now*) (t/hours 8))]
      (is (= #{[user-id true ass-hour8-2-20 1 ::assessment-reminder/activation]}
             (activation-reminders hour8))))))

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
           (late-reminders *now*)))))

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
           (late-reminders *now*)))))

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
           (late-reminders *now*)))))

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
           (late-reminders *now*)))))

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
           (late-reminders *now*)))))

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
           (late-reminders *now*)))))