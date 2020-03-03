(ns bass4.test.reqs-assessment
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.now :as now]
            [bass4.test.core :refer :all]
            [bass4.services.auth :as auth-service]
            [bass4.services.user :as user-service]
            [bass4.test.assessment-utils :refer :all]
            [bass4.db.core :as db]
            [clj-time.core :as t]
            [bass4.instrument.validation :as i-validation]
            [bass4.assessment.statuses :as assessment-statuses]))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(use-fixtures
  :each
  random-date-tz-fixture)

(deftest assessment-requests
  (binding [auth-service/double-auth-code (constantly "666777")]
    (let [top-priority     (create-assessment! project-double-auth-assessment-series
                                               {"Scope"                                    0
                                                "WelcomeText"                              "Welcome top-priority"
                                                "ThankYouText"                             "Thanks top"
                                                "CompetingAssessmentsPriority"             10
                                                "CompetingAssessmentsAllowSwallow"         1
                                                "CompetingAssessmentsShowTextsIfSwallowed" 1})
          top-top-priority (create-assessment! project-double-auth-assessment-series
                                               {"Scope"                                    1
                                                "WelcomeText"                              "top top welcome"
                                                "ThankYouText"                             "top top top thanks"
                                                "CompetingAssessmentsPriority"             2
                                                "CompetingAssessmentsAllowSwallow"         1
                                                "CompetingAssessmentsShowTextsIfSwallowed" 0})
          merge-hide-texts (create-assessment! project-double-auth-assessment-series
                                               {"Scope"                                    1
                                                "WelcomeText"                              "no-welcome"
                                                "ThankYouText"                             "no-thanks"
                                                "CompetingAssessmentsPriority"             20
                                                "CompetingAssessmentsAllowSwallow"         1
                                                "CompetingAssessmentsShowTextsIfSwallowed" 0})
          no-text          (create-assessment! project-double-auth-assessment-series
                                               {"Scope"        0
                                                "WelcomeText"  ""
                                                "ThankYouText" ""})]
      (link-instrument! top-top-priority 4431)              ; HAD
      (link-instrument! top-top-priority 4743)              ; Agoraphobic Cognitions Questionnaire
      (link-instrument! top-priority 286)                   ; AAQ
      (link-instrument! top-priority 4743)                  ; Agoraphobic Cognitions Questionnaire
      (link-instrument! top-priority 4568)                  ; PHQ-9
      (link-instrument! merge-hide-texts 4488)              ; WHODAS clinician rated
      (link-instrument! merge-hide-texts 4431)              ; HAD
      (link-instrument! no-text 4568)                       ; PHQ-9
      (link-instrument! no-text 4431)                       ; HAD

      ;; Login, double auth and then welcome text
      (let [user-id (create-user-with-password! {"SMSNumber" "00"})]
        (create-participant-administration! user-id top-priority 1 {:date (midnight (now/now))})
        (-> *s*
            (visit "/login" :request-method :post :params {:username user-id :password user-id})
            (has (status? 302))
            (follow-redirect)
            (has (some-text? "666777"))
            (visit "/double-auth" :request-method :post :params {:code "666777"})
            (has (status? 302))
            (follow-redirect)
            (follow-redirect)
            (has (some-text? "Welcome top-priority"))))

      ;; Answers validation fail
      (binding [i-validation/*validate-answers? true]
        (let [user-id (create-user-with-password! {"SMSNumber" "00"})]
          (create-participant-administration! user-id top-priority 1 {:date (midnight (now/now))})
          (-> *s*
              (visit "/login" :request-method :post :params {:username user-id :password user-id})
              (visit "/double-auth" :request-method :post :params {:code "666777"})
              (has (status? 302))
              (follow-redirect)
              (follow-redirect)
              (visit "/user/assessments" :request-method :post :params {:instrument-id 286 :items "tjosan" :specifications "tjosan"})
              (has (status? 400))
              (visit "/user/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
              ;; Answers validation inactivated
              #_(has (status? 400))
              (has (status? 302)))))

      ;; Group assessment - complete run through
      (let [group-id (create-group! project-double-auth)
            user-id  (create-user-with-password! {"SMSNumber" "00"
                                                  "Group"     group-id})]
        (create-participant-administration! user-id top-priority 1 {:date (midnight (now/now))})
        (create-group-administration! group-id top-top-priority 1 {:date (midnight (now/now))})
        (-> *s*
            (visit "/login" :request-method :post :params {:username user-id :password user-id})
            (visit "/double-auth" :request-method :post :params {:code "666777"})
            (follow-redirect)
            (follow-redirect)
            (has (some-text? "Welcome top-priority"))
            (has (some-text? "top top welcome"))
            (visit "/user/assessments")
            (has (some-text? "HAD"))
            (visit "/user/assessments" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"})
            (has (status? 302))
            (follow-redirect)
            (has (some-text? "Agoraphobic"))
            (visit "/user/assessments" :request-method :post :params {:instrument-id 4743 :items "{}" :specifications "{}"})
            ;; Posting answers to instrument not shown yet - advanced stuff!
            (visit "/user/assessments" :request-method :post :params {:instrument-id 4568 :items "{}" :specifications "{}"})
            (has (status? 302))
            (follow-redirect)
            (has (some-text? "AAQ"))
            (visit "/user/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
            (has (status? 302))
            (follow-redirect)
            (has (some-text? "top top top thanks"))
            (has (some-text? "Thanks top")))
        (is (= {:assessment-id top-priority, :assessment-index 1} (db/get-last-assessment {:user-id user-id})))
        (is (= 5 (count (db/get-completed-answers {:user-id user-id})))))

      ;; assessment-concurrent
      (let [group-id (create-group! project-double-auth)
            user-id  (create-user-with-password! {"SMSNumber" "00"
                                                  "Group"     group-id})]
        (create-participant-administration! user-id top-priority 1 {:date (midnight (now/now))})
        (create-group-administration! group-id top-top-priority 1 {:date (midnight (now/now))})
        (let [s1 (-> *s*
                     (visit "/login" :request-method :post :params {:username user-id :password user-id})
                     (visit "/double-auth" :request-method :post :params {:code "666777"}))
              s2 (-> *s*
                     (visit "/login" :request-method :post :params {:username user-id :password user-id})
                     (visit "/double-auth" :request-method :post :params {:code "666777"}))]
          (-> s1
              (has (status? 302))
              (follow-redirect)
              (follow-redirect)
              (has (some-text? "Welcome"))
              (has (some-text? "top top welcome")))
          (-> s2
              (has (status? 302))
              (follow-redirect)
              (follow-redirect)
              (has (some-text? "Welcome"))
              (has (some-text? "top top welcome"))
              (visit "/user/assessments")
              (has (some-text? "HAD")))
          (-> s1
              (visit "/user/assessments")
              (has (some-text? "HAD")))
          (-> s2
              (visit "/user/assessments")
              (has (some-text? "HAD"))
              ;; Posting answers to wrong instrument. Silently fails but "Something went wrong" is recorded
              ;; in request log.
              (visit "/user/assessments" :request-method :post :params {:instrument-id 6371 :items "{}" :specifications "{}"})
              (has (status? 302)))
          (-> s2
              (visit "/user/assessments")
              (has (some-text? "HAD"))
              (visit "/user/assessments" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"}))
          (-> s1
              (visit "/user/assessments")
              (has (some-text? "Agoraphobic"))
              (visit "/user/assessments" :request-method :post :params {:instrument-id 4743 :items "{}" :specifications "{}"}))
          (-> s2
              (visit "/user/assessments")
              (has (some-text? "AAQ")))
          (let [s3 (-> *s*
                       (visit "/login" :request-method :post :params {:username user-id :password user-id})
                       (visit "/double-auth" :request-method :post :params {:code "666777"}))]
            (-> s3
                (has (status? 302))
                (follow-redirect)
                (follow-redirect)
                (has (some-text? "Welcome top"))
                (visit "/user/assessments")
                (has (some-text? "AAQ")))
            (-> s1
                (visit "/user/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"}))
            (-> s3
                (visit "/user/assessments")
                (has (some-text? "PHQ"))
                (visit "/user/assessments" :request-method :post :params {:instrument-id 4568 :items "{}" :specifications "{}"}))
            (-> s2
                (visit "/user/assessments")
                (has (some-text? "Thanks top"))
                (visit "/user/assessments")
                ;; The assessment is now marked as completed and user is redirected...
                (follow-redirect)
                ;; ...to the user page, which redirects to the to-finished page
                (follow-redirect)
                ;; ...which clears the session and redirects to activities-finished page
                (follow-redirect)
                (has (some-text? "finished"))
                (visit "/user")
                (has (status? 403))))))

      ;; Swallow texts - show when not merged
      (let [group-id (create-group! project-double-auth)
            user-id  (create-user-with-password! {"SMSNumber" "00"
                                                  "Group"     group-id})]
        (create-group-administration! group-id merge-hide-texts 1 {:date (midnight (now/now))})
        (-> *s*
            (visit "/login" :request-method :post :params {:username user-id :password user-id})
            (visit "/double-auth" :request-method :post :params {:code "666777"})
            (follow-redirect)
            (follow-redirect)
            (has (some-text? "no-welcome"))
            (visit "/user/assessments" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"})
            (visit "/user/assessments" :request-method :post :params {:instrument-id 4568 :items "{}" :specifications "{}"})
            (has (status? 302))
            (follow-redirect)
            (has (some-text? "no-thanks"))))

      ;; Swallow texts - not show
      (let [group-id (create-group! project-double-auth)
            user-id  (create-user-with-password! {"SMSNumber" "00"
                                                  "Group"     group-id})]
        (create-participant-administration! user-id top-priority 1 {:date (midnight (now/now))})
        (create-group-administration! group-id top-top-priority 1 {:date (midnight (now/now))})
        (create-group-administration! group-id merge-hide-texts 1 {:date (midnight (now/now))})
        (-> *s*
            (visit "/login" :request-method :post :params {:username user-id :password user-id})
            (visit "/double-auth" :request-method :post :params {:code "666777"})
            (follow-redirect)
            (follow-redirect)
            (has (some-text? "Welcome top-priority"))
            (has (some-text? "top top welcome"))
            (fn-not-text? "no-welcome")
            (visit "/user/assessments" :request-method :post :params {:instrument-id 4743 :items "{}" :specifications "{}"})
            (visit "/user/assessments" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"})
            (visit "/user/assessments" :request-method :post :params {:instrument-id 4568 :items "{}" :specifications "{}"})
            (visit "/user/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
            (visit "/user/assessments" :request-method :post :params {:instrument-id 4488 :items "{}" :specifications "{}"})
            (has (status? 302))
            (follow-redirect)
            (has (some-text? "top top top thanks"))
            (has (some-text? "Thanks top"))
            (fn-not-text? "no-thanks"))))))

(deftest empty-assessment
  (binding [auth-service/double-auth-code (constantly "666777")]
    (let [user-id   (user-service/create-user! project-double-auth)
          empty-ass (create-assessment! project-double-auth-assessment-series
                                        {"Scope" 0})]
      (create-participant-administration! user-id empty-ass 1 {:date (midnight (now/now))})
      (user-service/update-user-properties! user-id {:username user-id :password user-id "SMSNumber" "000"})
      (let [statuses (assessment-statuses/user-administrations-statuses db/*db*
                                                                        (now/now)
                                                                        user-id)]
        (is (= 1 (count statuses)))
        (is (zero? (-> statuses
                       (first)
                       :date-completed))))
      (-> *s*
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (visit "/double-auth" :request-method :post :params {:code "666777"})
          (follow-redirect)
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "no activities")))
      (let [statuses (assessment-statuses/user-administrations-statuses db/*db*
                                                                        (now/now)
                                                                        user-id)]
        (is (= 1 (count statuses)))
        (is (< 0 (-> statuses
                     (first)
                     :date-completed)))))))

(deftest clinician-rated-assessment
  (binding [auth-service/double-auth-code (constantly "666777")]
    (let [group      (create-group! project-double-auth)
          user-id    (user-service/create-user! project-double-auth {:group group})
          clin-ass-p (create-assessment! project-double-auth-assessment-series
                                         {"Scope"               0
                                          "ClinicianAssessment" 1})
          clin-ass-g (create-assessment! project-double-auth-assessment-series
                                         {"Scope"               1
                                          "ClinicianAssessment" 1})]
      (link-instrument! clin-ass-p 4431)                    ; HAD
      (link-instrument! clin-ass-g 286)                     ; AAQ
      (create-participant-administration! user-id clin-ass-p 1 {:date (midnight (now/now))})
      (create-participant-administration! user-id clin-ass-g 1)
      (create-group-administration! user-id clin-ass-g 1 {:date (midnight (now/now))})

      (user-service/update-user-properties! user-id {:username user-id :password user-id "SMSNumber" "000"})
      (let [statuses (assessment-statuses/user-administrations-statuses db/*db*
                                                                        (now/now)
                                                                        user-id)]
        (is (= 2 (count statuses)))
        (is [0 0] (map :date-completed statuses)))
      (-> *s*
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (visit "/double-auth" :request-method :post :params {:code "666777"})
          (follow-redirect)
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "no activities")))
      (let [statuses (assessment-statuses/user-administrations-statuses db/*db*
                                                                        (now/now)
                                                                        user-id)]
        (is (= 2 (count statuses)))
        (is [0 0] (map :date-completed statuses))))))

(deftest custom-assessment
  (let [user-id (user-service/create-user! project-no-double-auth)]
    (user-service/update-user-properties! user-id {:username user-id :password user-id})
    (create-custom-assessment! user-id [286 4743] (now/now))
    (-> *s*
        (visit "/login" :request-method :post :params {:username user-id :password user-id})
        (has (status? 302))
        (follow-redirect)
        (follow-redirect)
        (has (some-text? "AAQ"))
        (visit "/user/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
        (follow-redirect)
        (has (some-text? "Agoraphobic"))
        (visit "/user/assessments" :request-method :post :params {:instrument-id 4743 :items "{}" :specifications "{}"})
        (follow-redirect)
        (follow-redirect)
        (follow-redirect)
        (follow-redirect)
        (has (some-text? "no more activities")))))