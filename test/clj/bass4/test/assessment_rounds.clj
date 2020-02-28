(ns ^:eftest/synchronized
  bass4.test.assessment-rounds
  (:require [clojure.test :refer :all]
            [bass4.db.core :refer [*db*] :as db]
            [bass4.assessment.ongoing :as assessment-ongoing]
            [bass4.test.assessment-utils :refer :all]
            [bass4.test.core :refer :all]
            [bass4.services.user :as user-service]
            [bass4.instrument.answers-services :as instrument-answers]
            [bass4.assessment.administration :as administration]
            [clojure.tools.logging :as log]))

(use-fixtures
  :once
  test-fixtures)

(use-fixtures
  :each
  random-date-tz-fixture)

(def create-answers! @#'instrument-answers/create-answers!)

(defn ongoing-assessments
  [now user-id]
  (let [res (assessment-ongoing/ongoing-assessments* db/*db* now user-id)]
    (into #{} (mapv #(vector (:assessment-id %) (:assessment-index %)) res))))

(deftest rounds-normal
  (let [top-priority          (create-assessment! {"Scope"                                    0
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
        no-text               (create-assessment! {"Scope"        0
                                                   "WelcomeText"  ""
                                                   "ThankYouText" ""})]
    (link-instrument! top-top-priority 4431)                ; HAD
    (link-instrument! top-top-priority 4743)                ; Agoraphobic Cognitions Questionnaire
    (link-instrument! top-priority 286)                     ; AAQ
    (link-instrument! top-priority 4743)                    ; Agoraphobic Cognitions Questionnaire
    (link-instrument! top-priority 4568)                    ; PHQ-9
    (link-instrument! second-priority-alone 4488)           ; WHODAS clinician rated
    (link-instrument! second-priority-alone 4431)           ; HAD
    (link-instrument! no-text 4568)                         ; PHQ-9
    (link-instrument! no-text 4431)                         ; HAD

    ;; Three ongoing assessments
    (let [user-id1 (user-service/create-user! project-ass1-id)
          adm-ttp  (create-participant-administration!
                     user-id1 top-top-priority 1 {:date (midnight *now*)})
          adm-tp   (create-participant-administration!
                     user-id1 top-priority 1 {:date (midnight *now*)})
          adm-spa  (create-participant-administration!
                     user-id1 second-priority-alone 1 {:date (midnight *now*)})]

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
             (->> (assessment-ongoing/ongoing-assessments* db/*db* *now* user-id1)
                  (administration/generate-assessment-round user-id1)
                  (map #(select-keys % [:batch-id :step :texts :instrument-id :administration-id]))))))

    ;; (Partially) completed assessments
    (let [user-id2 (user-service/create-user! project-ass1-id)
          adm-ttp2 (create-participant-administration!
                     user-id2 top-top-priority 1 {:date (midnight *now*)})
          adm-tp2  (create-participant-administration!
                     user-id2 top-priority 1 {:date (midnight *now*)})
          _        (create-participant-administration!
                     user-id2 second-priority-alone 1 {:date           (midnight *now*)
                                                       "DateCompleted" 1})]
      (instrument-answers/save-answers!
        (create-answers! adm-ttp2 4431)
        {})
      (is (= (list {:batch-id          0,
                    :step              0,
                    :texts             (pr-str '("WTTP" "WTP"))
                    :instrument-id     nil,
                    :administration-id nil}
                   {:batch-id          0,
                    :step              1,
                    :texts             nil,
                    :instrument-id     4743,
                    :administration-id adm-ttp2}
                   {:batch-id          0,
                    :step              2,
                    :texts             nil,
                    :instrument-id     286,
                    :administration-id adm-tp2}
                   {:batch-id          0,
                    :step              3,
                    :texts             nil,
                    :instrument-id     4743,
                    :administration-id adm-tp2}
                   {:batch-id          0,
                    :step              4,
                    :texts             nil,
                    :instrument-id     4568,
                    :administration-id adm-tp2}
                   {:batch-id          0,
                    :step              5,
                    :texts             (pr-str '("TTTP" "TTP")),
                    :instrument-id     nil,
                    :administration-id nil})
             (->> (assessment-ongoing/ongoing-assessments* db/*db* *now* user-id2)
                  (administration/generate-assessment-round user-id2)
                  (map #(select-keys % [:batch-id :step :texts :instrument-id :administration-id]))))))
    ;; No texts
    (let [user-id3 (user-service/create-user! project-ass1-id)
          adm-nt   (create-participant-administration!
                     user-id3 no-text 1 {:date (midnight *now*)})]
      (is (= (list {:batch-id          0,
                    :step              0,
                    :texts             nil,
                    :instrument-id     4568,
                    :administration-id adm-nt}
                   {:batch-id          0,
                    :step              1,
                    :texts             nil,
                    :instrument-id     4431,
                    :administration-id adm-nt})
             (->> (assessment-ongoing/ongoing-assessments* db/*db* *now* user-id3)
                  (administration/generate-assessment-round user-id3)
                  (map #(select-keys % [:batch-id :step :texts :instrument-id :administration-id]))))))))