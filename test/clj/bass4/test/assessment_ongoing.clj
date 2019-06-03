(ns bass4.test.assessment-ongoing
  (:require [clj-time.core :as t]
            [bass4.db.core :refer [*db*] :as db]
            [bass4.assessment.ongoing :as assessment-ongoing]
            [bass4.test.assessment-utils :refer :all]
            [bass4.test.core :refer :all]
            [clojure.test :refer :all]
            [bass4.services.bass :as bass]
            [bass4.services.user :as user-service]
            [bass4.utils :as utils]))

(use-fixtures
  :once
  test-fixtures)

(def custom-participant-id 654412)
(def custom-administration-id 654430)
(def custom-assessment-id 654429)

(defn midnight
  ([] (midnight (t/now)))
  ([now] (utils/to-unix (bass/local-midnight now))))

(defn midnight+d
  ([plus-days] (midnight+d plus-days (t/now)))
  ([plus-days now]
   (utils/to-unix (t/plus (bass/local-midnight now) (t/days plus-days)))))

(defn ongoing-assessments
  [user-id]
  (let [res (assessment-ongoing/ongoing-assessments user-id)]
    (into #{} (mapv #(vector (:assessment-id %) (:assessment-index %)) res))))

(deftest group-assessment
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-id {:group group-id})]
    ; Today
    (create-group-administration!
      group-id group-single 1 {:date (midnight)})
    (create-group-administration!
      group-id ass-group-weekly 4 {:date (midnight)})
    ; Wrong scope
    (create-group-administration!
      group-id ass-individual-single 1 {:date (midnight)})
    ; Tomorrow
    (create-group-administration!
      group-id ass-group-weekly 1 {:date (+ (midnight+d 1))})
    (is (= #{[group-single 1] [ass-group-weekly 4]} (ongoing-assessments user-id)))))

(deftest group-assessment-timelimit
  ; Timelimit within
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-id {:group group-id})]
    ; Today
    (create-group-administration!
      group-id group-single 1 {:date (midnight+d -3)})
    ; Tomorrow
    (is (= #{[group-single 1]} (ongoing-assessments user-id))))

  ; Timelimit too late
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-id {:group group-id})]
    ; Today
    (create-group-administration!
      group-id group-single 1 {:date (midnight+d -4)})
    ; Tomorrow
    (is (= #{} (ongoing-assessments user-id)))))

(deftest individual-assessment-in-group
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-id {:group group-id})]
    ; Today
    (create-participant-administration!
      user-id ass-individual-single 1 {:date (midnight)})
    (create-participant-administration!
      user-id ass-individual-weekly-no-remind 4 {:date (midnight)})
    ; Wrong scope
    (create-participant-administration!
      user-id group-single 1 {:date (midnight)})
    ; Tomorrow
    (create-participant-administration!
      user-id ass-individual-weekly-no-remind 1 {:date (+ (midnight+d 1))})
    (is (= #{[ass-individual-single 1] [ass-individual-weekly-no-remind 4]}
           (ongoing-assessments user-id)))))

(deftest individual-assessment-no-group
  (let [user-id (user-service/create-user! project-id)]
    ; Today
    (create-participant-administration!
      user-id ass-individual-single 1 {:date (midnight)})
    (create-participant-administration!
      user-id ass-individual-weekly-no-remind 4 {:date (midnight)})
    ; Wrong scope
    (create-participant-administration!
      user-id group-single 1 {:date (midnight)})
    ; Tomorrow
    (create-participant-administration!
      user-id ass-individual-weekly-no-remind 1 {:date (+ (midnight+d 1))})
    (is (= #{[ass-individual-single 1] [ass-individual-weekly-no-remind 4]} (ongoing-assessments user-id)))))

(deftest individual+group-assessment
  ; In group
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-id {:group group-id})]
    (create-participant-administration!
      user-id ass-individual-single 1 {:date (midnight)})
    (create-group-administration!
      group-id group-single 1 {:date (midnight)})
    (is (= #{[ass-individual-single 1] [group-single 1]} (ongoing-assessments user-id)))))

(deftest index-overflow-assessment
  ; In group
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-id {:group group-id})]
    (create-participant-administration!
      user-id ass-individual-weekly-no-remind 5 {:date (midnight)})
    (create-group-administration!
      group-id ass-group-weekly 5 {:date (midnight)})
    (is (= #{} (ongoing-assessments user-id)))))

(deftest individual-assessment-timelimit
  ; Timelimit within
  (let [user-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user-id ass-individual-single 1 {:date (midnight+d -3)})
    (is (= #{[ass-individual-single 1]} (ongoing-assessments user-id))))

  ; Timelimit too late
  (let [user-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user-id ass-individual-single 1 {:date (midnight+d -4)})
    (is (= #{} (ongoing-assessments user-id)))))

(deftest individual+group-inactive-assessment
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-id {:group group-id})]
    (create-participant-administration!
      user-id ass-individual-single 1 {:date (midnight)})
    (create-group-administration!
      group-id ass-individual-single 1 {:active 0})
    (create-group-administration!
      group-id group-single 1 {:date (midnight)})
    (create-participant-administration!
      user-id group-single 1 {:active 0})
    (is (= #{} (ongoing-assessments user-id)))))

(deftest manual-assessment
  ;; Create administrations in reverse order to check sorting of them
  (let [user-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user-id ass-individual-manual-repeat 1 {:date (midnight+d -3)})
    (is (= #{[ass-individual-manual-repeat 1]} (ongoing-assessments user-id))))

  (let [user-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user-id ass-individual-manual-repeat 2 {:date (midnight+d -2)})
    (create-participant-administration!
      user-id ass-individual-manual-repeat 1 {:date (midnight+d -3)})
    ; Only last assessment active
    (is (= #{[ass-individual-manual-repeat 2]} (ongoing-assessments user-id))))

  (let [user-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user-id ass-individual-manual-repeat 2 {:date (midnight+d -3)})
    (create-participant-administration!
      user-id ass-individual-manual-repeat 1 {:date (midnight+d -2)})
    ; Only last assessment active - even if it has lower start date
    (is (= #{[ass-individual-manual-repeat 2]} (ongoing-assessments user-id))))

  (let [user-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user-id ass-individual-manual-repeat 3 {:date (midnight)})
    (create-participant-administration!
      user-id ass-individual-manual-repeat 1 {:date (midnight)})
    ; Only last assessment active - even if one is skipped
    (is (= #{[ass-individual-manual-repeat 3]} (ongoing-assessments user-id))))

  (let [user-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user-id ass-individual-manual-repeat 3 {:date (midnight) :active 0})
    (create-participant-administration!
      user-id ass-individual-manual-repeat 1 {:date (midnight)})
    ; First assessment active - even later is inactive
    (is (= #{[ass-individual-manual-repeat 1]} (ongoing-assessments user-id)))))

(deftest clinician-assessment
  (let [user-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user-id ass-clinician 1 {:date (midnight)})
    (is (= #{} (ongoing-assessments user-id)))))

(deftest start-hour-assessment
  (let [user-id (user-service/create-user! project-id)]
    (create-participant-administration!
      user-id ass-hour-8 1 {:date (midnight)})
    (let [hour0 (bass/local-midnight)]
      (with-redefs [t/now (constantly hour0)]
        (is (= #{} (ongoing-assessments user-id)))))
    (let [hour7 (t/plus (bass/local-midnight) (t/hours 7))]
      (with-redefs [t/now (constantly hour7)]
        (is (= #{} (ongoing-assessments user-id)))))
    (let [hour8 (t/plus (bass/local-midnight) (t/hours 8))]
      (with-redefs [t/now (constantly hour8)]
        (is (= #{[ass-hour-8 1]} (ongoing-assessments user-id)))))))

(deftest no-administrations
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-id {:group group-id})]
    (is (= #{} (ongoing-assessments user-id)))))

(deftest custom-assessment
  (db/update-object-properties! {:table-name "c_participantadministration"
                                 :object-id  custom-administration-id
                                 :updates    {:date (midnight)}})
  (is (= #{[custom-assessment-id 1]}
         (ongoing-assessments custom-participant-id))))

(deftest full-return-assessment-group-assessment
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-id {:group group-id})]
    ; Today
    (create-group-administration!
      group-id group-single 1 {:date (midnight)})
    (let [res (first (assessment-ongoing/ongoing-assessments user-id))]
      ; Tomorrow
      (is (= #{:thank-you-text
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
      (is (= true (sub-map? {:thank-you-text "thankyou"
                             :welcome-text   "welcome"
                             :instruments    [4743 286]}
                            res))))))

(deftest full-return-assessment-individual-assessment
  (let [user-id (user-service/create-user! project-id)]
    ; Today
    (create-participant-administration!
      user-id ass-individual-single 1 {:date (midnight)})
    (let [res (first (assessment-ongoing/ongoing-assessments user-id))]
      (is (= #{:thank-you-text
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
      (is (= true (sub-map? {:thank-you-text "thankyou1"
                             :welcome-text   "welcome1"
                             ; Clinician instrument removed
                             :instruments    [4458 1609]}
                            res))))))