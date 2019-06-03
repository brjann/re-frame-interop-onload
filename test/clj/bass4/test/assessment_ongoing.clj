(ns bass4.test.assessment-ongoing
  (:require [clj-time.core :as t]
            [bass4.db.core :refer [*db*] :as db]
            [bass4.assessment.ongoing :as assessment-ongoing]
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

(def custom-participant-id 654412)
(def custom-administration-id 654430)
(def custom-assessment-id 654429)

(defn ongoing-assessments
  [now user-id]
  (let [res (assessment-ongoing/ongoing-assessments* db/*db* now user-id)]
    (into #{} (mapv #(vector (:assessment-id %) (:assessment-index %)) res))))

(deftest group-assessment
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-id {:group group-id})]
    ; Today
    (create-group-administration!
      group-id ass-group-single-2-3 1 {:date (midnight *now*)})
    (create-group-administration!
      group-id ass-group-weekly-3-4 4 {:date (midnight *now*)})
    ; Wrong scope
    (create-group-administration!
      group-id ass-individual-single-0 1 {:date (midnight *now*)})
    ; Tomorrow
    (create-group-administration!
      group-id ass-group-weekly-3-4 1 {:date (+ (midnight+d 1 *now*))})
    (is (= #{[ass-group-single-2-3 1] [ass-group-weekly-3-4 4]} (ongoing-assessments *now* user-id)))))

(deftest group-assessment-timelimit
  ; Timelimit within
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-id {:group group-id})]
    (create-group-administration!
      group-id ass-group-single-2-3 1 {:date (midnight+d -3 *now*)})
    (is (= #{[ass-group-single-2-3 1]} (ongoing-assessments *now* user-id))))

  ; Timelimit too late
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-id {:group group-id})]
    (create-group-administration!
      group-id ass-group-single-2-3 1 {:date (midnight+d -40 *now*)})
    (is (= #{} (ongoing-assessments *now* user-id)))))

(deftest individual-assessment-in-group
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-id {:group group-id})]
    ; Today
    (create-participant-administration!
      user-id ass-individual-single-0 1 {:date (midnight *now*)})
    (create-participant-administration!
      user-id ass-individual-weekly-no-remind 4 {:date (midnight *now*)})
    ; Wrong scope
    (create-participant-administration!
      user-id ass-group-single-2-3 1 {:date (midnight *now*)})
    ; Tomorrow
    (create-participant-administration!
      user-id ass-individual-weekly-no-remind 1 {:date (+ (midnight+d 1 *now*))})
    (is (= #{[ass-individual-single-0 1] [ass-individual-weekly-no-remind 4]}
           (ongoing-assessments *now* user-id)))))

(deftest individual-assessment-no-group
  (let [user-id (user-service/create-user! project-id)]
    ; Today
    (create-participant-administration!
      user-id ass-individual-single-0 1 {:date (midnight *now*)})
    (create-participant-administration!
      user-id ass-individual-weekly-no-remind 4 {:date (midnight *now*)})
    ; Wrong scope
    (create-participant-administration!
      user-id ass-group-single-2-3 1 {:date (midnight *now*)})
    ; Tomorrow
    (create-participant-administration!
      user-id ass-individual-weekly-no-remind 1 {:date (midnight+d 1 *now*)})
    (is (= #{[ass-individual-single-0 1] [ass-individual-weekly-no-remind 4]}
           (ongoing-assessments *now* user-id)))))

(deftest individual+group-assessment
  ; In group
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-id {:group group-id})]
    (create-participant-administration!
      user-id ass-individual-single-0 1 {:date (midnight *now*)})
    (create-group-administration!
      group-id ass-group-single-2-3 1 {:date (midnight *now*)})
    (is (= #{[ass-individual-single-0 1] [ass-group-single-2-3 1]} (ongoing-assessments *now* user-id)))))

(deftest index-overflow-assessment
  ; In group
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-id {:group group-id})]
    (create-participant-administration!
      user-id ass-individual-weekly-no-remind 5 {:date (midnight *now*)})
    (create-group-administration!
      group-id ass-group-weekly-3-4 5 {:date (midnight *now*)})
    (is (= #{} (ongoing-assessments *now* user-id)))))

(deftest individual-assessment-timelimit
  ; Timelimit within
  (let [user-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user-id ass-individual-single-0 1 {:date (midnight+d -3 *now*)})
    (is (= #{[ass-individual-single-0 1]} (ongoing-assessments *now* user-id))))

  ; Timelimit too late
  (let [user-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user-id ass-individual-single-0 1 {:date (midnight+d -4 *now*)})
    (is (= #{} (ongoing-assessments *now* user-id)))))

(deftest individual+group-inactive-assessment
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-id {:group group-id})]
    (create-participant-administration!
      user-id ass-individual-single-0 1 {:date (midnight *now*)})
    (create-group-administration!
      group-id ass-individual-single-0 1 {:active 0})
    (create-group-administration!
      group-id ass-group-single-2-3 1 {:date (midnight *now*)})
    (create-participant-administration!
      user-id ass-group-single-2-3 1 {:active 0})
    (is (= #{} (ongoing-assessments *now* user-id)))))

(deftest manual-assessment
  ;; Create administrations in reverse order to check sorting of them
  (let [user-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user-id ass-individual-manual-5-10 1 {:date (midnight+d -3 *now*)})
    (is (= #{[ass-individual-manual-5-10 1]} (ongoing-assessments *now* user-id))))

  (let [user-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user-id ass-individual-manual-5-10 2 {:date (midnight+d -2 *now*)})
    (create-participant-administration!
      user-id ass-individual-manual-5-10 1 {:date (midnight+d -3 *now*)})
    ; Only last assessment active
    (is (= #{[ass-individual-manual-5-10 2]} (ongoing-assessments *now* user-id))))

  (let [user-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user-id ass-individual-manual-5-10 2 {:date (midnight+d -3 *now*)})
    (create-participant-administration!
      user-id ass-individual-manual-5-10 1 {:date (midnight+d -2 *now*)})
    ; Only last assessment active - even if it has lower start date
    (is (= #{[ass-individual-manual-5-10 2]} (ongoing-assessments *now* user-id))))

  (let [user-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user-id ass-individual-manual-5-10 3 {:date (midnight *now*)})
    (create-participant-administration!
      user-id ass-individual-manual-5-10 1 {:date (midnight *now*)})
    ; Only last assessment active - even if one is skipped
    (is (= #{[ass-individual-manual-5-10 3]} (ongoing-assessments *now* user-id))))

  (let [user-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user-id ass-individual-manual-5-10 3 {:date (midnight *now*) :active 0})
    (create-participant-administration!
      user-id ass-individual-manual-5-10 1 {:date (midnight *now*)})
    ; First assessment active - even later is inactive
    (is (= #{[ass-individual-manual-5-10 1]} (ongoing-assessments *now* user-id)))))

(deftest clinician-assessment
  (let [user-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user-id ass-clinician 1 {:date (midnight *now*)})
    (is (= #{} (ongoing-assessments *now* user-id)))))

(deftest start-hour-assessment
  (let [user-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user-id ass-hour8-2-20 1 {:date (midnight *now*)})
    (let [hour0 (midnight-joda *now*)]
      (is (= #{} (ongoing-assessments hour0 user-id))))
    (let [hour7 (t/plus (midnight-joda *now*) (t/hours 7))]
      (is (= #{} (ongoing-assessments hour7 user-id))))
    (let [hour8 (t/plus (midnight-joda *now*) (t/hours 8))]
      (is (= #{[ass-hour8-2-20 1]}
             (ongoing-assessments hour8 user-id))))
    (let [tomorrow (t/plus (midnight-joda *now*) (t/days 1))]
      (is (= #{[ass-hour8-2-20 1]} (ongoing-assessments tomorrow user-id))))))

(deftest no-administrations
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-id {:group group-id})]
    (is (= #{} (ongoing-assessments *now* user-id)))))

(deftest custom-assessment
  (db/update-object-properties! {:table-name "c_participantadministration"
                                 :object-id  custom-administration-id
                                 :updates    {:date (midnight *now*)}})
  (is (= #{[custom-assessment-id 1]}
         (ongoing-assessments *now* custom-participant-id))))

(deftest full-return-assessment-group-assessment
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-id {:group group-id})]
    ; Today
    (create-group-administration!
      group-id ass-group-single-2-3 1 {:date (midnight *now*)})
    (let [res (first (assessment-ongoing/ongoing-assessments* db/*db* *now* user-id))]
      ; Tomorrow
      (is (= #{:user-id
               :thank-you-text
               :repetition-type
               :assessment-index
               :show-texts-if-swallowed?
               :date-completed
               :assessment-id
               :participant-activation-date
               :repetition-interval
               :scope
               :instruments
               :welcome-text
               :shuffle-instruments
               :priority
               :group-administration-id
               :active?
               :status
               :clinician-rated?
               :repetitions
               :group-activation-date
               :time-limit
               :is-record?
               :participant-administration-id
               :allow-swallow?
               :assessment-name
               :activation-hour}
             (into #{} (keys res))))
      (is (= true (sub-map? {:user-id        user-id
                             :thank-you-text "thankyou"
                             :welcome-text   "welcome"
                             :instruments    [4743 286]}
                            res))))))

(deftest full-return-assessment-individual-assessment
  (let [user-id (user-service/create-user! project-id)]
    ; Today
    (create-participant-administration!
      user-id ass-individual-single-0 1 {:date (midnight *now*)})
    (let [res (first (assessment-ongoing/ongoing-assessments* db/*db* *now* user-id))]
      (is (= #{:user-id
               :thank-you-text
               :repetition-type
               :assessment-index
               :show-texts-if-swallowed?
               :date-completed
               :assessment-id
               :participant-activation-date
               :repetition-interval
               :scope
               :instruments
               :welcome-text
               :shuffle-instruments
               :priority
               :group-administration-id
               :active?
               :status
               :clinician-rated?
               :repetitions
               :group-activation-date
               :time-limit
               :is-record?
               :participant-administration-id
               :allow-swallow?
               :assessment-name
               :activation-hour}
             (into #{} (keys res))))
      (is (= true (sub-map? {:user-id        user-id
                             :thank-you-text "thankyou1"
                             :welcome-text   "welcome1"
                             ; Clinician instrument removed
                             :instruments    [4458 1609]}
                            res))))))