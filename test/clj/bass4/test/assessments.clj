(ns bass4.test.assessments
  (:require [clj-time.core :as t]
            [bass4.db.core :refer [*db*] :as db]
            [bass4.assessment.services :as assessments]
            [bass4.assessment.administration :as administration]
            [bass4.test.core :refer :all]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [bass4.services.bass :as bass]
            [bass4.services.user :as user-service]
            [bass4.utils :as utils]))

(use-fixtures
  :once
  test-fixtures)

(def x 8)

(defn get-ass-1-pending
  []
  (with-redefs [t/now                         (constantly (t/date-time 2017 05 30 17 16 00))
                db/get-user-group             (constantly {:group-name nil :group-id nil})
                db/get-user-assessment-series (constantly {:assessment-series-id 535756})
                db/get-user-assessments       (constantly (get-edn "ass-1-series-ass"))
                db/get-user-administrations   (constantly (get-edn "ass-1-adms"))]
    (assessments/ongoing-assessments 535795)))

(defn get-ass-1-rounds
  [pending]
  (with-redefs [t/now (constantly (t/date-time 1986 10 14))]
    (doall (administration/generate-assessment-round 234 pending))))

(deftest ass-1
  (let [pending (get-ass-1-pending)]
    (is (= (get-edn "ass-1-res") pending))
    (is (= (get-ass-1-rounds pending)
           (get-edn "ass-1-rounds")))))

(defn get-ass-3-pending
  []
  (with-redefs [t/now                                        (constantly (t/date-time 2017 06 12 9 40 0))
                db/get-user-group                            (constantly {:group-name nil :group-id nil})
                db/get-user-assessment-series                (constantly {:assessment-series-id 535756})
                db/get-user-assessments                      (constantly (get-edn "ass-3-series-ass"))
                db/get-user-administrations                  (constantly (get-edn "ass-3-adms"))
                db/get-assessments-instruments               (constantly (get-edn "ass-3-instruments"))
                db/get-administration-completed-instruments  (constantly ())
                db/get-administration-additional-instruments (constantly ())]
    (assessments/ongoing-assessments 535899)))

(defn get-ass-3-rounds
  [pending]
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (doall (administration/generate-assessment-round 535899 pending))))

(deftest ass-3
  (let [pending (get-ass-3-pending)]
    (is (= (get-edn "ass-3-res") pending))
    (is (= (get-edn "ass-3-rounds")
           (get-ass-3-rounds pending)))))

(defn get-ass-4-pending
  []
  (with-redefs [t/now                                        (constantly (t/date-time 2017 06 12 9 40 0))
                db/get-user-group                            (constantly {:group-name nil :group-id nil})
                db/get-user-assessment-series                (constantly {:assessment-series-id 535756})
                db/get-user-assessments                      (constantly (get-edn "ass-4-series-ass"))
                db/get-user-administrations                  (constantly (get-edn "ass-4-adms"))
                db/get-assessments-instruments               (constantly (get-edn "ass-4-instruments"))
                db/get-administration-completed-instruments  (constantly ())
                db/get-administration-additional-instruments (constantly ())]
    (assessments/ongoing-assessments 536048)))

(defn get-ass-4-rounds
  [pending]
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (doall (administration/generate-assessment-round 536048 pending))))

(deftest ass-4
  (let [pending (get-ass-4-pending)]
    (is (= (get-edn "ass-4-res") pending))
    (is (= (get-ass-4-rounds pending)
           (get-edn "ass-4-rounds")))))

(defn get-ass-5-pending
  []
  (with-redefs [t/now                                        (constantly (t/date-time 2017 06 12 9 40 0))
                db/get-user-group                            (constantly {:group-name nil :group-id nil})
                db/get-user-assessment-series                (constantly {:assessment-series-id 535756})
                db/get-user-assessments                      (constantly (get-edn "ass-5-series-ass"))
                db/get-user-administrations                  (constantly (get-edn "ass-5-adms"))
                db/get-assessments-instruments               (constantly (get-edn "ass-5-instruments"))
                db/get-administration-completed-instruments  (constantly '({:administration-id 536049, :instrument-id 286}))
                db/get-administration-additional-instruments (constantly ())]
    (assessments/ongoing-assessments 536048)))

(defn get-ass-5-rounds
  [pending]
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (doall (administration/generate-assessment-round 536048 pending))))

(deftest ass-5
  (let [pending (get-ass-5-pending)]
    (is (= (get-edn "ass-5-res") pending))
    (is (= (get-ass-5-rounds pending)
           (get-edn "ass-5-rounds")))))

(defn get-ass-6-pending
  []
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (sort-by :assessment-id (assessments/ongoing-assessments 536124))))

(defn get-ass-6-rounds
  [pending]
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (doall (administration/generate-assessment-round 536124 pending))))

(deftest ass-6-group
  (let [pending (get-ass-6-pending)]
    (is (= (get-edn "ass-6-res") pending))
    (is (= (get-ass-6-rounds pending)
           (get-edn "ass-6-rounds")))))

(defn get-ass-7-pending
  []
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (sort-by :assessment-id (assessments/ongoing-assessments 536140))))

(defn get-ass-7-rounds
  [pending]
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (doall (administration/generate-assessment-round 536140 pending))))

(deftest ass-7-group-some-done
  (let [pending (get-ass-7-pending)]
    (is (= (get-edn "ass-7-res") pending))
    (is (= (get-ass-7-rounds pending)
           (get-edn "ass-7-rounds")))))

(defn get-ass-8-pending
  []
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 12 33 0))]
    (assessments/ongoing-assessments 535899)))

(defn get-ass-8-rounds
  [pending]
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (doall (administration/generate-assessment-round 535899 pending))))

(deftest ass-8-no-text
  (let [pending (get-ass-8-pending)]
    (is (= (get-edn "ass-8-res") pending))
    (is (= (get-edn "ass-8-rounds")
           (get-ass-8-rounds pending)))))


;; -----------------------
;;   NEW "DYNAMIC" TESTS
;; -----------------------

(defn midnight
  ([] (midnight (t/now)))
  ([now] (utils/to-unix (bass/local-midnight now))))

(defn midnight+d
  ([plus-days] (midnight+d plus-days (t/now)))
  ([plus-days now]
   (utils/to-unix (t/plus (bass/local-midnight now) (t/days plus-days)))))

(def ass-project-id 653627)
(def ass-group-single 653630)
(def ass-group-weekly 653631)
(def ass-individual-single 653632)
(def ass-individual-weekly 653633)
(def ass-individual-manual-repeat 653634)
(def ass-clinician 654215)
(def ass-hour-8 654411)
(def ass-custom-participant 654412)
(def ass-custom-administration 654430)
(def ass-custom-assessment 654429)

(defn create-group!
  []
  (:objectid (db/create-bass-object! {:class-name    "cGroup"
                                      :parent-id     ass-project-id
                                      :property-name "Groups"})))

(defn create-group-administration!
  [group-id assessment-id assessment-index & [properties]]
  (let [administration-id (:objectid (db/create-bass-object! {:class-name    "cGroupAdministration"
                                                              :parent-id     group-id
                                                              :property-name "Administrations"}))]
    (when properties
      (db/update-object-properties! {:table-name "c_groupadministration"
                                     :object-id  administration-id
                                     :updates    (merge {:assessment      assessment-id
                                                         :assessmentindex assessment-index
                                                         :active          1}
                                                        properties)}))))

(defn create-participant-administration!
  [user-id assessment-id assessment-index & [properties]]
  (let [administration-id (:objectid (db/create-bass-object! {:class-name    "cParticipantAdministration"
                                                              :parent-id     user-id
                                                              :property-name "Administrations"}))]
    (when properties
      (db/update-object-properties! {:table-name "c_participantadministration"
                                     :object-id  administration-id
                                     :updates    (merge {:assessment      assessment-id
                                                         :assessmentindex assessment-index
                                                         :active          1
                                                         :deleted         0}
                                                        properties)}))))

(defn ongoing-assessments
  [user-id]
  (let [res (assessments/ongoing-assessments user-id)]
    (into #{} (mapv #(vector (:assessment-id %) (:assessment-index %)) res))))

(deftest group-assessment
  (let [group-id (create-group!)
        user-id  (user-service/create-user! ass-project-id {:group group-id})]
    ; Today
    (create-group-administration!
      group-id ass-group-single 1 {:date (midnight)})
    (create-group-administration!
      group-id ass-group-weekly 4 {:date (midnight)})
    ; Wrong scope
    (create-group-administration!
      group-id ass-individual-single 1 {:date (midnight)})
    ; Tomorrow
    (create-group-administration!
      group-id ass-group-weekly 1 {:date (+ (midnight+d 1))})
    (is (= #{[ass-group-single 1] [ass-group-weekly 4]} (ongoing-assessments user-id)))))

(deftest group-assessment-timelimit
  ; Timelimit within
  (let [group-id (create-group!)
        user-id  (user-service/create-user! ass-project-id {:group group-id})]
    ; Today
    (create-group-administration!
      group-id ass-group-single 1 {:date (midnight+d -3)})
    ; Tomorrow
    (is (= #{[ass-group-single 1]} (ongoing-assessments user-id))))

  ; Timelimit too late
  (let [group-id (create-group!)
        user-id  (user-service/create-user! ass-project-id {:group group-id})]
    ; Today
    (create-group-administration!
      group-id ass-group-single 1 {:date (midnight+d -4)})
    ; Tomorrow
    (is (= #{} (ongoing-assessments user-id)))))

(deftest individual-assessment-in-group
  (let [group-id (create-group!)
        user-id  (user-service/create-user! ass-project-id {:group group-id})]
    (log/debug user-id)
    ; Today
    (create-participant-administration!
      user-id ass-individual-single 1 {:date (midnight)})
    (create-participant-administration!
      user-id ass-individual-weekly 4 {:date (midnight)})
    ; Wrong scope
    (create-participant-administration!
      user-id ass-group-single 1 {:date (midnight)})
    ; Tomorrow
    (create-participant-administration!
      user-id ass-individual-weekly 1 {:date (+ (midnight+d 1))})
    (is (= #{[ass-individual-single 1] [ass-individual-weekly 4]}
           (ongoing-assessments user-id)))))

(deftest individual-assessment-no-group
  (let [user-id (user-service/create-user! ass-project-id)]
    ; Today
    (create-participant-administration!
      user-id ass-individual-single 1 {:date (midnight)})
    (create-participant-administration!
      user-id ass-individual-weekly 4 {:date (midnight)})
    ; Wrong scope
    (create-participant-administration!
      user-id ass-group-single 1 {:date (midnight)})
    ; Tomorrow
    (create-participant-administration!
      user-id ass-individual-weekly 1 {:date (+ (midnight+d 1))})
    (is (= #{[ass-individual-single 1] [ass-individual-weekly 4]} (ongoing-assessments user-id)))))

(deftest individual+group-assessment
  ; In group
  (let [group-id (create-group!)
        user-id  (user-service/create-user! ass-project-id {:group group-id})]
    (create-participant-administration!
      user-id ass-individual-single 1 {:date (midnight)})
    (create-group-administration!
      group-id ass-group-single 1 {:date (midnight)})
    (is (= #{[ass-individual-single 1] [ass-group-single 1]} (ongoing-assessments user-id)))))

(deftest index-overflow-assessment
  ; In group
  (let [group-id (create-group!)
        user-id  (user-service/create-user! ass-project-id {:group group-id})]
    (create-participant-administration!
      user-id ass-individual-weekly 5 {:date (midnight)})
    (create-group-administration!
      group-id ass-group-weekly 5 {:date (midnight)})
    (is (= #{} (ongoing-assessments user-id)))))

(deftest individual-assessment-timelimit
  ; Timelimit within
  (let [user-id (user-service/create-user! ass-project-id)]
    (create-participant-administration!
      user-id ass-individual-single 1 {:date (midnight+d -3)})
    (is (= #{[ass-individual-single 1]} (ongoing-assessments user-id))))

  ; Timelimit too late
  (let [user-id (user-service/create-user! ass-project-id)]
    (create-participant-administration!
      user-id ass-individual-single 1 {:date (midnight+d -4)})
    (is (= #{} (ongoing-assessments user-id)))))

(deftest individual+group-inactive-assessment
  (let [group-id (create-group!)
        user-id  (user-service/create-user! ass-project-id {:group group-id})]
    (create-participant-administration!
      user-id ass-individual-single 1 {:date (midnight)})
    (create-group-administration!
      group-id ass-individual-single 1 {:active 0})
    (create-group-administration!
      group-id ass-group-single 1 {:date (midnight)})
    (create-participant-administration!
      user-id ass-group-single 1 {:active 0})
    (is (= #{} (ongoing-assessments user-id)))))

(deftest manual-assessment
  (let [user-id (user-service/create-user! ass-project-id)]
    (create-participant-administration!
      user-id ass-individual-manual-repeat 1 {:date (midnight+d -3)})
    (is (= #{[ass-individual-manual-repeat 1]} (ongoing-assessments user-id))))

  (let [user-id (user-service/create-user! ass-project-id)]
    (create-participant-administration!
      user-id ass-individual-manual-repeat 1 {:date (midnight+d -3)})
    (create-participant-administration!
      user-id ass-individual-manual-repeat 2 {:date (midnight+d -2)})
    ; Only last assessment active
    (is (= #{[ass-individual-manual-repeat 2]} (ongoing-assessments user-id))))

  (let [user-id (user-service/create-user! ass-project-id)]
    (create-participant-administration!
      user-id ass-individual-manual-repeat 1 {:date (midnight+d -2)})
    (create-participant-administration!
      user-id ass-individual-manual-repeat 2 {:date (midnight+d -3)})
    ; Only last assessment active - even if it has lower start date
    (is (= #{[ass-individual-manual-repeat 2]} (ongoing-assessments user-id)))))

(deftest clinician-assessment
  (let [user-id (user-service/create-user! ass-project-id)]
    (create-participant-administration!
      user-id ass-clinician 1 {:date (midnight)})
    (is (= #{} (ongoing-assessments user-id)))))

(deftest start-hour-assessment
  (let [user-id (user-service/create-user! ass-project-id)]
    (create-participant-administration!
      user-id ass-hour-8 1 {:date (midnight)})
    (is (= #{} (ongoing-assessments user-id)))
    (let [hour7 (t/plus (bass/local-midnight) (t/hours 7))]
      (with-redefs [t/now (constantly hour7)]
        (is (= #{} (ongoing-assessments user-id)))))
    (let [hour8 (t/plus (bass/local-midnight) (t/hours 8))]
      (with-redefs [t/now (constantly hour8)]
        (is (= #{[ass-hour-8 1]} (ongoing-assessments user-id)))))))

(deftest custom-assessment
  (db/update-object-properties! {:table-name "c_participantadministration"
                                 :object-id  ass-custom-administration
                                 :updates    {:date (midnight)}})
  (is (= #{[ass-custom-assessment 1]}
         (ongoing-assessments ass-custom-participant))))

(deftest full-return-assessment-group-assessment
  (let [group-id (create-group!)
        user-id  (user-service/create-user! ass-project-id {:group group-id})]
    ; Today
    (create-group-administration!
      group-id ass-group-single 1 {:date (midnight)})
    (let [res (first (assessments/ongoing-assessments user-id))]
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
                             :date-completed 0M
                             :instruments    [4743 286]}
                            res))))))

(deftest full-return-assessment-individual-assessment
  (let [user-id (user-service/create-user! ass-project-id)]
    ; Today
    (create-participant-administration!
      user-id ass-individual-single 1 {:date (midnight)})
    (let [res (first (assessments/ongoing-assessments user-id))]
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
                             :date-completed 0M
                             ; Clinician instrument removed
                             :instruments    [4458 1609]}
                            res))))))
