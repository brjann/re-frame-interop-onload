(ns bass4.test.assessment-rounds
  (:require [clojure.test :refer :all]
            [bass4.db.core :refer [*db*] :as db]
            [bass4.assessment.ongoing :as assessment-ongoing]
            [bass4.test.assessment-utils :refer :all]
            [bass4.test.core :refer :all]
            [bass4.services.user :as user-service]
            [bass4.assessment.administration :as administration]))

(use-fixtures
  :once
  test-fixtures)

(use-fixtures
  :each
  random-date-tz-fixture)

(defn ongoing-assessments
  [now user-id]
  (let [res (assessment-ongoing/ongoing-assessments* db/*db* now user-id)]
    (into #{} (mapv #(vector (:assessment-id %) (:assessment-index %)) res))))

(deftest rounds-1
  (let [user-id               (user-service/create-user! project-ass1-id)
        top-priority          (create-assessment! {"Scope"                                    0
                                                   "WelcomeText"                              "WTP"
                                                   "ThankYouText"                             "TTP"
                                                   "CompetingAssessmentsPriority"             10
                                                   "CompetingAssessmentsAllowSwallow"         1
                                                   "CompetingAssessmentsShowTextsIfSwallowed" 1})
        second-priority-alone (create-assessment! {"Scope"                                    0
                                                   "WelcomeText"                              "WSPA"
                                                   "ThankYouText"                             "TSPA"
                                                   "CompetingAssessmentsPriority"             20
                                                   "CompetingAssessmentsAllowSwallow"         0
                                                   "CompetingAssessmentsShowTextsIfSwallowed" 1})
        top-top-priority      (create-assessment! {"Scope"                                    0
                                                   "WelcomeText"                              "WTTP"
                                                   "ThankYouText"                             "TTTP"
                                                   "CompetingAssessmentsPriority"             2
                                                   "CompetingAssessmentsAllowSwallow"         1
                                                   "CompetingAssessmentsShowTextsIfSwallowed" 0})
        adm-ttp               (create-participant-administration!
                                user-id top-top-priority 1 {:date (midnight *now*)})
        adm-tp                (create-participant-administration!
                                user-id top-priority 1 {:date (midnight *now*)})
        adm-spa               (create-participant-administration!
                                user-id second-priority-alone 1 {:date (midnight *now*)})]
    (link-instrument! top-top-priority 4431)                ; HAD
    (link-instrument! top-top-priority 4743)                ; Agoraphobic Cognitions Questionnaire
    (link-instrument! top-priority 286)                     ; AAQ
    (link-instrument! top-priority 4743)                    ; Agoraphobic Cognitions Questionnaire
    (link-instrument! top-priority 4568)                    ; PHQ-9
    (link-instrument! second-priority-alone 4488)           ; WHODAS clinician rated
    (link-instrument! second-priority-alone 4431)           ; HAD

    (is (= (list {:batch-id          0,
                  :step              0,
                  :texts             (pr-str '("WTTP" "WTP"))
                  :instrument-id     nil,
                  :administration-id nil}
                 {:batch-id          0,
                  :step              1,
                  :texts             nil,
                  :instrument-id     4431,
                  :administration-id adm-ttp}
                 {:batch-id          0,
                  :step              2,
                  :texts             nil,
                  :instrument-id     4743,
                  :administration-id adm-ttp}
                 {:batch-id          0,
                  :step              3,
                  :texts             nil,
                  :instrument-id     286,
                  :administration-id adm-tp}
                 {:batch-id          0,
                  :step              4,
                  :texts             nil,
                  :instrument-id     4743,
                  :administration-id adm-tp}
                 {:batch-id          0,
                  :step              5,
                  :texts             nil,
                  :instrument-id     4568,
                  :administration-id adm-tp}
                 {:batch-id          0,
                  :step              6,
                  :texts             (pr-str '("TTTP" "TTP")),
                  :instrument-id     nil,
                  :administration-id nil}
                 {:batch-id          1,
                  :step              7,
                  :texts             (pr-str '("WSPA")),
                  :instrument-id     nil,
                  :administration-id nil}
                 {:batch-id          1,
                  :step              8,
                  :texts             nil,
                  :instrument-id     4431,
                  :administration-id adm-spa}
                 {:batch-id          1,
                  :step              9,
                  :texts             (pr-str '("TSPA")),
                  :instrument-id     nil,
                  :administration-id nil})
           (->> (assessment-ongoing/ongoing-assessments* db/*db* *now* user-id)
                (administration/generate-assessment-round user-id)
                (map #(select-keys % [:batch-id :step :texts :instrument-id :administration-id])))))

    ; Today
    ;(create-group-administration!
    ;  group-id ass-G-s-2-3-p0 1 {:date (midnight *now*)})
    ;(create-group-administration!
    ;  group-id ass-G-week-e+s-3-4-p10 4 {:date (midnight *now*)})
    ;; Wrong scope
    ;(create-group-administration!
    ;  group-id ass-I-s-0-p100-message 1 {:date (midnight *now*)})
    ;; Tomorrow
    ;(create-group-administration!
    ;  group-id ass-G-week-e+s-3-4-p10 1 {:date (+ (midnight+d 1 *now*))})
    ;(is (= #{[ass-G-s-2-3-p0 1] [ass-G-week-e+s-3-4-p10 4]}
    ;       (ongoing-assessments *now* user-id)))
    ;(is (= #{[ass-I-s-0-p100-message 1 :assessment-status/scoped-missing]
    ;         [ass-G-s-2-3-p0 1 :assessment-status/ongoing]
    ;         [ass-G-week-e+s-3-4-p10 4 :assessment-status/ongoing]
    ;         [ass-G-week-e+s-3-4-p10 1 :assessment-status/waiting]}
    ;       (user-statuses *now* user-id)))
    ;(is (= #{[ass-G-s-2-3-p0 1 :assessment-status/ongoing]
    ;         [ass-G-week-e+s-3-4-p10 4 :assessment-status/ongoing]
    ;         [ass-G-week-e+s-3-4-p10 1 :assessment-status/waiting]
    ;         [ass-I-s-0-p100-message 1 :assessment-status/scoped-missing]}
    ;(group-statuses *now* group-id)
    ))
