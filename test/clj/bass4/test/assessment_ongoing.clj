(ns bass4.test.assessment-ongoing
  (:require [clj-time.core :as t]
            [clojure.test :refer :all]
            [bass4.db.core :refer [*db*] :as db]
            [bass4.assessment.ongoing :as assessment-ongoing]
            [bass4.test.assessment-utils :refer :all]
            [bass4.test.core :refer :all]
            [bass4.services.user :as user-service]
            [bass4.utils :as utils]
            [bass4.db.orm-classes :as orm]
            [clojure.tools.logging :as log]))

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
  (let [group-id               (create-group!)
        user-id                (user-service/create-user! project-ass1-id {:group group-id})
        ass-G-s-2-3-p0         (create-assessment! {"Scope" 1})
        ass-G-week-e+s-3-4-p10 (create-assessment! {"Scope"                    1
                                                    "RepetitionType"           3
                                                    "Repetitions"              4
                                                    "CustomRepetitionInterval" 7})
        ass-I-s-0-p100-message (create-assessment! {"Scope" 0})]
    ; Today
    (create-group-administration!
      group-id ass-G-s-2-3-p0 1 {:date (midnight *now*)})
    (create-group-administration!
      group-id ass-G-week-e+s-3-4-p10 4 {:date (midnight *now*)})
    ; Wrong scope
    (create-group-administration!
      group-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
    ; Tomorrow
    (create-group-administration!
      group-id ass-G-week-e+s-3-4-p10 1 {:date (+ (midnight+d 1 *now*))})
    (is (= #{[ass-G-s-2-3-p0 1] [ass-G-week-e+s-3-4-p10 4]}
           (ongoing-assessments *now* user-id)))
    (is (= #{[ass-I-s-0-p100-message 1 :assessment-status/scoped-missing]
             [ass-G-s-2-3-p0 1 :assessment-status/ongoing]
             [ass-G-week-e+s-3-4-p10 4 :assessment-status/ongoing]
             [ass-G-week-e+s-3-4-p10 1 :assessment-status/waiting]}
           (user-statuses *now* user-id)))
    (is (= #{[ass-G-s-2-3-p0 1 :assessment-status/ongoing]
             [ass-G-week-e+s-3-4-p10 4 :assessment-status/ongoing]
             [ass-G-week-e+s-3-4-p10 1 :assessment-status/waiting]
             [ass-I-s-0-p100-message 1 :assessment-status/scoped-missing]}
           (group-statuses *now* group-id)))))

(deftest group-assessment-mysql-old-super-join-fail
  (let [group-id               (create-group!)
        user-id                (user-service/create-user! project-ass1-id {:group group-id})
        ass-G-week-e+s-3-4-p10 (create-assessment! {"Scope"                    1
                                                    "RepetitionType"           3
                                                    "Repetitions"              4
                                                    "CustomRepetitionInterval" 7})]
    (create-group-administration!
      group-id ass-G-week-e+s-3-4-p10 3 {:date (midnight+d -7 *now*)})
    (create-group-administration!
      group-id ass-G-week-e+s-3-4-p10 4 {:date (midnight *now*)})
    (create-participant-administration!
      user-id ass-G-week-e+s-3-4-p10 3)
    (is (= #{[ass-G-week-e+s-3-4-p10 4]}
           (ongoing-assessments *now* user-id)))
    (is (= #{[ass-G-week-e+s-3-4-p10 3 :assessment-status/date-passed]
             [ass-G-week-e+s-3-4-p10 4 :assessment-status/ongoing]}
           (user-statuses *now* user-id)))
    (is (= #{[ass-G-week-e+s-3-4-p10 3 :assessment-status/date-passed]
             [ass-G-week-e+s-3-4-p10 4 :assessment-status/ongoing]}
           (group-statuses *now* group-id)))))

(deftest group-assessment-timelimit
  ; Timelimit within
  (let [group-id       (create-group!)
        user-id        (user-service/create-user! project-ass1-id {:group group-id})
        ass-G-s-2-3-p0 (create-assessment! {"Scope" 1})]
    (create-group-administration!
      group-id ass-G-s-2-3-p0 1 {:date (midnight+d -3 *now*)})
    (is (= #{[ass-G-s-2-3-p0 1]} (ongoing-assessments *now* user-id)))
    (is (= #{[ass-G-s-2-3-p0 1 :assessment-status/ongoing]}
           (user-statuses *now* user-id)))
    (is (= #{[ass-G-s-2-3-p0 1 :assessment-status/ongoing]}
           (group-statuses *now* group-id))))

  ; Timelimit too late
  (let [group-id       (create-group!)
        user-id        (user-service/create-user! project-ass1-id {:group group-id})
        ass-G-s-2-3-p0 (create-assessment! {"Scope"     1
                                            "TimeLimit" 40})]
    (create-group-administration!
      group-id ass-G-s-2-3-p0 1 {:date (midnight+d -40 *now*)})
    (is (= #{} (ongoing-assessments *now* user-id)))
    (is (= #{[ass-G-s-2-3-p0 1 :assessment-status/date-passed]}
           (user-statuses *now* user-id)))
    (is (= #{[ass-G-s-2-3-p0 1 :assessment-status/date-passed]}
           (group-statuses *now* group-id)))))

(deftest individual-assessment-in-group-completed
  (let [group-id      (create-group!)
        user-id       (user-service/create-user! project-ass1-id {:group group-id})
        ass-I         (create-assessment! {"Scope" 0})
        ass-I-W*4-TL4 (create-assessment! {"Scope"                    0
                                           "RepetitionType"           3
                                           "Repetitions"              4
                                           "CustomRepetitionInterval" 7
                                           "TimeLimit"                4})
        adm1          (create-participant-administration!
                        user-id ass-I 1 {:date (midnight *now*)})
        adm2          (create-participant-administration!
                        user-id ass-I-W*4-TL4 1 {:date (midnight *now*)})
        ass-G         (create-assessment! {"Scope" 1})]
    ; Wrong scope
    (create-participant-administration!
      user-id ass-G 1 {:date (midnight *now*)})
    ; Tomorrow
    (create-participant-administration!
      user-id ass-I-W*4-TL4 4 {:date (+ (midnight+d 1 *now*))})
    (is (= #{[ass-I 1]
             [ass-I-W*4-TL4 1]}
           (ongoing-assessments *now* user-id)))
    (is (= #{[ass-G 1 :assessment-status/scoped-missing]
             [ass-I 1 :assessment-status/ongoing]
             [ass-I-W*4-TL4 1 :assessment-status/ongoing]
             [ass-I-W*4-TL4 4 :assessment-status/waiting]}
           (user-statuses *now* user-id)))
    (is (= #{}
           (group-statuses *now* group-id)))
    (orm/update-object-properties! "c_participantadministration" adm1 {"datecompleted" (utils/to-unix *now*)})
    (orm/update-object-properties! "c_participantadministration" adm2 {"datecompleted" (utils/to-unix *now*)})
    (is (= #{}
           (ongoing-assessments *now* user-id)))
    (is (= #{[ass-G 1 :assessment-status/scoped-missing]
             [ass-I 1 :assessment-status/completed]
             [ass-I-W*4-TL4 1 :assessment-status/completed]
             [ass-I-W*4-TL4 4 :assessment-status/waiting]}
           (user-statuses *now* user-id)))))

(deftest individual-assessment-no-group
  (let [user-id                (user-service/create-user! project-ass1-id)
        ass-I-s-0-p100-message (create-assessment! {"Scope" 0})
        ass-I-week-noremind    (create-assessment! {"Scope"                    0
                                                    "RepetitionType"           3
                                                    "Repetitions"              4
                                                    "CustomRepetitionInterval" 7})
        ass-G-s-2-3-p0         (create-assessment! {"Scope" 1})]
    ; Today
    (create-participant-administration!
      user-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
    (create-participant-administration!
      user-id ass-I-week-noremind 1 {:date (midnight *now*)})
    ; Wrong scope
    (create-participant-administration!
      user-id ass-G-s-2-3-p0 1 {:date (midnight *now*)})
    ; Tomorrow
    (create-participant-administration!
      user-id ass-I-week-noremind 4 {:date (midnight+d 1 *now*)})
    (is (= #{[ass-I-s-0-p100-message 1]
             [ass-I-week-noremind 1]}
           (ongoing-assessments *now* user-id)))
    (is (= #{[ass-G-s-2-3-p0 1 :assessment-status/scoped-missing]
             [ass-I-s-0-p100-message 1 :assessment-status/ongoing]
             [ass-I-week-noremind 1 :assessment-status/ongoing]
             [ass-I-week-noremind 4 :assessment-status/waiting]}
           (user-statuses *now* user-id)))))

(deftest individual+group-assessment
  ; In group
  (let [group-id               (create-group!)
        user-id                (user-service/create-user! project-ass1-id {:group group-id})
        ass-I-s-0-p100-message (create-assessment! {"Scope" 0})
        ass-G-s-2-3-p0         (create-assessment! {"Scope" 1})]
    (create-participant-administration!
      user-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
    (create-group-administration!
      group-id ass-G-s-2-3-p0 1 {:date (midnight *now*)})
    (create-group-administration!
      group-id ass-I-s-0-p100-message 1)
    (is (= #{[ass-I-s-0-p100-message 1]
             [ass-G-s-2-3-p0 1]}
           (ongoing-assessments *now* user-id)))
    (is (= #{[ass-I-s-0-p100-message 1 :assessment-status/ongoing]
             [ass-G-s-2-3-p0 1 :assessment-status/ongoing]}
           (user-statuses *now* user-id)))
    (is (= #{[ass-G-s-2-3-p0 1 :assessment-status/ongoing]}
           (group-statuses *now* group-id)))))

(deftest index-overflow-assessment
  ; In group
  (let [group-id               (create-group!)
        user-id                (user-service/create-user! project-ass1-id {:group group-id})
        ass-I-week-noremind    (create-assessment! {"Scope"                    0
                                                    "RepetitionType"           3
                                                    "Repetitions"              4
                                                    "CustomRepetitionInterval" 7})
        ass-G-week-e+s-3-4-p10 (create-assessment! {"Scope"                    1
                                                    "RepetitionType"           3
                                                    "Repetitions"              4
                                                    "CustomRepetitionInterval" 7})]
    (create-participant-administration!
      user-id ass-I-week-noremind 5 {:date (midnight *now*)})
    (create-group-administration!
      group-id ass-G-week-e+s-3-4-p10 5 {:date (midnight *now*)})
    (is (= #{} (ongoing-assessments *now* user-id)))))

(deftest individual-assessment-timelimit
  ; Timelimit within
  (let [ass-I-s-0-p100-message (create-assessment! {"Scope"     0
                                                    "TimeLimit" 4})]
    (let [user-id (user-service/create-user! project-ass1-id)]
      (create-participant-administration!
        user-id ass-I-s-0-p100-message 1 {:date (midnight+d -3 *now*)})
      (is (= #{[ass-I-s-0-p100-message 1]} (ongoing-assessments *now* user-id))))

    ; Timelimit too late
    (let [user-id (user-service/create-user! project-ass1-id)]
      (create-participant-administration!
        user-id ass-I-s-0-p100-message 1 {:date (midnight+d -4 *now*)})
      (is (= #{} (ongoing-assessments *now* user-id))))))

(deftest individual+group-inactive-assessment
  (let [group-id               (create-group!)
        user-id                (user-service/create-user! project-ass1-id {:group group-id})
        ass-I-s-0-p100-message (create-assessment! {"Scope" 0})
        ass-G-s-2-3-p0         (create-assessment! {"Scope" 1})]
    (create-participant-administration!
      user-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
    (create-group-administration!
      group-id ass-I-s-0-p100-message 1 {:active 0})
    (create-group-administration!
      group-id ass-G-s-2-3-p0 1 {:date (midnight *now*)})
    (create-participant-administration!
      user-id ass-G-s-2-3-p0 1 {:active 0})
    (is (= #{} (ongoing-assessments *now* user-id)))))

(deftest manual-assessment
  ;; Create administrations in reverse order to check sorting of them
  (let [ass-I-manual-s-5-10-q (create-assessment! {"Scope"                        0
                                                   "SendSMSWhenActivated"         1
                                                   "CompetingAssessmentsPriority" 10
                                                   "RepetitionType"               2
                                                   "Repetitions"                  4})]
    (let [user-id (user-service/create-user! project-ass1-id)]
      (create-participant-administration!
        user-id ass-I-manual-s-5-10-q 1 {:date (midnight+d -3 *now*)})
      (is (= #{[ass-I-manual-s-5-10-q 1]} (ongoing-assessments *now* user-id))))

    (let [user-id (user-service/create-user! project-ass1-id)]
      (create-participant-administration!
        user-id ass-I-manual-s-5-10-q 1 {:date (midnight+d -3 *now*)})
      (create-participant-administration!
        user-id ass-I-manual-s-5-10-q 2 {:date (midnight+d -2 *now*)})
      (create-participant-administration!
        user-id ass-I-manual-s-5-10-q 3 {:date (midnight+d -1 *now*)})
      ; Only last assessment active
      (is (= #{[ass-I-manual-s-5-10-q 3]} (ongoing-assessments *now* user-id))))

    (let [user-id (user-service/create-user! project-ass1-id)]
      (create-participant-administration!
        user-id ass-I-manual-s-5-10-q 2 {:date (midnight+d -3 *now*)})
      (create-participant-administration!
        user-id ass-I-manual-s-5-10-q 1 {:date (midnight+d -2 *now*)})
      ; Only last assessment active - even if it has lower start date
      (is (= #{[ass-I-manual-s-5-10-q 2]} (ongoing-assessments *now* user-id))))

    (let [user-id (user-service/create-user! project-ass1-id)]
      (create-participant-administration!
        user-id ass-I-manual-s-5-10-q 3 {:date (midnight *now*)})
      (create-participant-administration!
        user-id ass-I-manual-s-5-10-q 1 {:date (midnight *now*)})
      ; Only last assessment active - even if one is skipped
      (is (= #{[ass-I-manual-s-5-10-q 3]} (ongoing-assessments *now* user-id))))

    ; Later inactive assessment does not inactivate manual assessment
    (let [user-id (user-service/create-user! project-ass1-id)]
      (create-participant-administration!
        user-id ass-I-manual-s-5-10-q 1 {:date (midnight *now*)})
      (create-participant-administration!
        user-id ass-I-manual-s-5-10-q 3 {:date (midnight *now*) :active 0})
      ; First assessment active - even later is inactive
      (is (= #{[ass-I-manual-s-5-10-q 1]} (ongoing-assessments *now* user-id))))

    ; Later inactive group assessment does not inactivate manual assessment
    (let [group1-id (create-group!)
          user1-id  (user-service/create-user! project-ass1-id {:group group1-id})]
      (create-participant-administration!
        user1-id ass-I-manual-s-5-10-q 2 {:date (midnight *now*)})
      (create-group-administration!
        group1-id ass-I-manual-s-5-10-q 4 {:active 0})
      (is (= #{[ass-I-manual-s-5-10-q 2]} (ongoing-assessments *now* user1-id))))))

(deftest clinician-assessment
  (let [user-id         (user-service/create-user! project-ass1-id)
        ass-I-clinician (create-assessment! {"Scope"               0
                                             "ClinicianAssessment" 1})]
    (create-participant-administration!
      user-id ass-I-clinician 1 {:date (midnight *now*)})
    (is (= #{} (ongoing-assessments *now* user-id)))))

(deftest start-hour-assessment
  (let [user-id          (user-service/create-user! project-ass1-id)
        ass-I-hour8-2-20 (create-assessment! {"Scope"          0
                                              "ActivationHour" 8})]
    (create-participant-administration!
      user-id ass-I-hour8-2-20 1 {:date (midnight *now*)})
    (let [hour0 (midnight-joda *now*)]
      (is (= #{} (ongoing-assessments hour0 user-id))))
    (let [hour7 (t/plus (midnight-joda *now*) (t/hours 7))]
      (is (= #{} (ongoing-assessments hour7 user-id))))
    (let [hour8 (t/plus (midnight-joda *now*) (t/hours 8))]
      (is (= #{[ass-I-hour8-2-20 1]}
             (ongoing-assessments hour8 user-id))))
    (let [tomorrow (t/plus (midnight-joda *now*) (t/days 1))]
      (is (= #{[ass-I-hour8-2-20 1]} (ongoing-assessments tomorrow user-id))))))

(deftest no-administrations
  (let [group-id (create-group!)
        user-id  (user-service/create-user! project-ass1-id {:group group-id})]
    (is (= #{} (ongoing-assessments *now* user-id)))))

(deftest unlinked-administration
  (let [user-id (user-service/create-user! project-ass1-id)]
    ; Today
    (create-participant-administration!
      user-id 666 1 {:date (midnight *now*)})
    ;; Does not crash
    (is (= #{} (ongoing-assessments *now* user-id)))))

(deftest change-project
  (let [user-id                (user-service/create-user! project-ass2-id)
        ass1-id                (create-assessment! project-ass2-assessment-series {"Scope" 0})
        adm1-id                (create-participant-administration!
                                 user-id ass1-id 1 {:date (midnight *now*)})
        ass-I-s-0-p100-message (create-assessment! {"Scope" 0})]
    (create-participant-administration!
      user-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
    (is (= #{[ass1-id 1]} (ongoing-assessments *now* user-id)))
    (orm/update-object-properties! "c_participant"
                                   user-id
                                   {"parentid"        project-ass2-pcollection-id
                                    "parentinterface" project-ass1-id})
    (orm/update-object-properties! "c_participantadministration"
                                   adm1-id
                                   {"parentinterface" project-ass1-id})
    (is (= #{[ass-I-s-0-p100-message 1]} (ongoing-assessments *now* user-id)))
    (is (= #{[ass1-id 1 :assessment-status/wrong-series]
             [ass-I-s-0-p100-message 1 :assessment-status/ongoing]}
           (user-statuses *now* user-id)))
    (orm/update-object-properties! "c_participantadministration"
                                   adm1-id
                                   {"datecompleted" (utils/to-unix *now*)})
    (is (= #{[ass1-id 1 :assessment-status/completed]
             [ass-I-s-0-p100-message 1 :assessment-status/ongoing]}
           (user-statuses *now* user-id)))))

(deftest custom-assessment
  (let [user-id (user-service/create-user! project-double-auth)
        ass-id  (create-custom-assessment! user-id [] (midnight *now*))]
    (is (= #{[ass-id 1]}
           (ongoing-assessments *now* user-id)))))

(deftest full-return-assessment-group-assessment
  (let [group-id       (create-group!)
        user-id        (user-service/create-user! project-ass1-id {:group group-id})
        ass-G-s-2-3-p0 (create-assessment! {"Scope"        1
                                            "WelcomeText"  "welcome"
                                            "ThankYouText" "thankyou"})]
    (link-instrument! ass-G-s-2-3-p0 4743)
    (link-instrument! ass-G-s-2-3-p0 286)
    (create-group-administration!
      group-id ass-G-s-2-3-p0 1 {:date (midnight *now*)})
    (let [res (first (assessment-ongoing/ongoing-assessments* db/*db* *now* user-id))]
      (is (= true (sub-map? {:user-id        user-id
                             :thank-you-text "thankyou"
                             :welcome-text   "welcome"
                             :instruments    [4743 286]}
                            res))))))

(deftest full-return-assessment-individual-assessment
  (let [user-id                (user-service/create-user! project-ass1-id)
        ass-I-s-0-p100-message (create-assessment! {"Scope"        0
                                                    "WelcomeText"  "welcome1"
                                                    "ThankYouText" "thankyou1"})]
    (link-instrument! ass-I-s-0-p100-message 4458)
    (link-instrument! ass-I-s-0-p100-message 1609)
    ; Today
    (create-participant-administration!
      user-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
    (let [res (first (assessment-ongoing/ongoing-assessments* db/*db* *now* user-id))]
      (is (= true (sub-map? {:user-id        user-id
                             :thank-you-text "thankyou1"
                             :welcome-text   "welcome1"
                             ; Clinician instrument removed
                             :instruments    [4458 1609]}
                            res))))))