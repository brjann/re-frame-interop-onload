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
            [bass4.assessment.statuses :as assessment-statuses]
            [clojure.data.json :as json]
            [bass4.instrument.answers-services :as instrument-answers]
            [bass4.instrument.flagger :as answers-flagger]
            [clojure.java.jdbc :as jdbc]
            [clojure.core.async :as a]
            [bass4.config :as config]))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(use-fixtures
  :each
  random-date-tz-fixture)

(defn top-priority-assessment!
  []
  (let [top-priority (create-assessment! project-double-auth-assessment-series
                                         {"Scope"                                    0
                                          "WelcomeText"                              "Welcome top-priority"
                                          "ThankYouText"                             "Thanks top"
                                          "CompetingAssessmentsPriority"             10
                                          "CompetingAssessmentsAllowSwallow"         1
                                          "CompetingAssessmentsShowTextsIfSwallowed" 1})]
    (link-instrument! top-priority 286)                     ; AAQ
    (link-instrument! top-priority 4743)                    ; Agoraphobic Cognitions Questionnaire
    (link-instrument! top-priority 4568)                    ; PHQ-9
    top-priority))

(defn top-top-priority-assessment!
  []
  (let [top-top-priority (create-assessment! project-double-auth-assessment-series
                                             {"Scope"                                    1
                                              "WelcomeText"                              "top top welcome"
                                              "ThankYouText"                             "top top top thanks"
                                              "CompetingAssessmentsPriority"             2
                                              "CompetingAssessmentsAllowSwallow"         1
                                              "CompetingAssessmentsShowTextsIfSwallowed" 0})]
    (link-instrument! top-top-priority 4431)                ; HAD
    (link-instrument! top-top-priority 4743)                ; Agoraphobic Cognitions Questionnaire
    top-top-priority))

(defn merge-hide-texts-assessment!
  []
  (let [merge-hide-texts (create-assessment! project-double-auth-assessment-series
                                             {"Scope"                                    1
                                              "WelcomeText"                              "no-welcome"
                                              "ThankYouText"                             "no-thanks"
                                              "CompetingAssessmentsPriority"             20
                                              "CompetingAssessmentsAllowSwallow"         1
                                              "CompetingAssessmentsShowTextsIfSwallowed" 0})]
    (link-instrument! merge-hide-texts 4488)                ; WHODAS clinician rated
    (link-instrument! merge-hide-texts 4431)                ; HAD
    merge-hide-texts))

(defn concurrent-test
  [user-id group-id top-priority top-top-priority]
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

(deftest concurrent
  (binding [auth-service/double-auth-code (constantly "666777")]
    (let [group-id         (create-group! project-double-auth)
          user-id          (create-user-with-password! {"SMSNumber" "00"
                                                        "Group"     group-id})
          top-priority     (top-priority-assessment!)
          top-top-priority (top-top-priority-assessment!)]
      (concurrent-test user-id group-id top-priority top-top-priority))))

(deftest concurrent-concurrent
  (binding [auth-service/double-auth-code (constantly "666777")]
    (let [top-priority     (top-priority-assessment!)
          top-top-priority (top-top-priority-assessment!)
          ;; 10 threads seems to "crash" the Cursive test runner and the whole test suite gives no output
          n                (if (config/env :dev-test) 10 2)
          tuples           (repeatedly n #(let [group-id (create-group! project-double-auth)
                                                user-id  (create-user-with-password! {"SMSNumber" "00"
                                                                                      "Group"     group-id})]
                                            [user-id group-id]))
          cs               (mapv (fn [[user-id group-id]]
                                   (a/thread
                                     (concurrent-test user-id group-id top-priority top-top-priority)
                                     user-id))
                                 tuples)
          _                (mapv #(a/<!! %) cs)
          user-ids         (map first tuples)
          res              (jdbc/query db/*db*
                                       (apply vector
                                              (str "SELECT count(*) FROM
                                      c_participantadministration AS cpa
                                      JOIN c_instrumentanswers AS cia
                                      ON cpa.ObjectId = cia.ParentId
                                      WHERE cpa.ParentId IN("
                                                   (apply str (interpose "," (repeat n "?")))
                                                   ") AND cia.DateCompleted > 0")
                                              user-ids))]
      (is (= (* n 5) (first (vals (first res))))))))

(deftest assessment-requests
  (let [top-priority     (top-priority-assessment!)
        top-top-priority (top-top-priority-assessment!)
        merge-hide-texts (merge-hide-texts-assessment!)]
    (binding [auth-service/double-auth-code (constantly "666777")]

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

(deftest no-texts
  (binding [auth-service/double-auth-code (constantly "666777")]
    (let [no-text (create-assessment! project-double-auth-assessment-series
                                      {"Scope"        0
                                       "WelcomeText"  ""
                                       "ThankYouText" ""})
          user-id (user-service/create-user! project-double-auth)]
      (link-instrument! no-text 4568)                       ; PHQ-9
      (link-instrument! no-text 4431)                       ; HAD
      (create-participant-administration! user-id no-text 1 {:date (midnight (now/now))})
      (user-service/update-user-properties! user-id {:username user-id :password user-id "SMSNumber" "000"})
      (-> *s*
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (visit "/double-auth" :request-method :post :params {:code "666777"})
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "PHQ-9"))
          (visit "/user/assessments" :request-method :post :params {:instrument-id 4568 :items "{}" :specifications "{}"})
          (follow-redirect)
          (has (some-text? "HAD"))
          (visit "/user/assessments" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"})
          (follow-redirect)
          (follow-redirect)
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "no more activities"))))))

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

(deftest answers-saved
  (let [user-id (user-service/create-user! project-no-double-auth)
        ass-id  (create-assessment! project-no-double-auth-ass-series
                                    {"Scope" 0})
        adm-id  (create-participant-administration! user-id ass-id 1 {:date (midnight (now/now))})
        item2   (inc (rand-int 7))]
    (link-instrument! ass-id 286)                           ; AAQ
    (user-service/update-user-properties! user-id {:username user-id :password user-id})
    (-> *s*
        (visit "/login" :request-method :post :params {:username user-id :password user-id})
        (has (status? 302))
        (follow-redirect)
        (follow-redirect)
        (has (some-text? "AAQ"))
        (visit "/user/assessments" :request-method :post :params {:instrument-id  286
                                                                  :items          (json/write-str {"293" (str item2), "300" "4", "295" "4", "302" "4", "299" "4", "296" "3", "294" "2", "301" "4", "298" "3", "292" "1"})
                                                                  :specifications "{}"})
        (follow-redirect)
        (follow-redirect)
        (follow-redirect)
        (follow-redirect)
        (has (some-text? "no more activities")))
    (let [answers (instrument-answers/get-answers adm-id 286)]
      (is (= (+ item2 37) (get (:sums answers) "sum"))))))

(deftest answers-flagged!
  (let [item2 (inc (rand-int 7))]
    (binding [answers-flagger/flagging-specs (constantly {project-no-double-auth [{:abbreviation "AAQ-2"
                                                                                   :condition    "@1==1"
                                                                                   :message      "Hell satan"}]
                                                          :global                [{:instrument-id 286
                                                                                   :condition     (str "sum==" (+ item2 37))}]})]
      (let [user-id (user-service/create-user! project-no-double-auth)
            ass-id  (create-assessment! project-no-double-auth-ass-series
                                        {"Scope" 0})
            ass-id2 (create-assessment! project-no-double-auth-ass-series
                                        {"Scope" 0})
            adm-id1 (create-participant-administration! user-id ass-id 1 {:date (midnight (now/now))})
            adm-id2 (create-participant-administration! user-id ass-id2 1 {:date (midnight (now/now))})]
        (link-instrument! ass-id 286)                       ; AAQ
        (link-instrument! ass-id2 286)                      ; AAQ
        (user-service/update-user-properties! user-id {:username user-id :password user-id})
        (-> *s*
            (visit "/login" :request-method :post :params {:username user-id :password user-id})
            (has (status? 302))
            (follow-redirect)
            (follow-redirect)
            (has (some-text? "AAQ"))
            (visit "/user/assessments" :request-method :post :params {:instrument-id  286
                                                                      :items          (json/write-str {"293" (str item2), "300" "4", "295" "4", "302" "4", "299" "4", "296" "3", "294" "2", "301" "4", "298" "3", "292" "1"})
                                                                      :specifications "{}"})
            (follow-redirect)
            (follow-redirect)
            (follow-redirect)
            (follow-redirect)
            (has (some-text? "no more activities")))
        (is (= 2 (count (jdbc/query db/*db* ["SELECT * FROM c_flag WHERE ParentId = ?" user-id]))))
        (is (= 1 (count (jdbc/query db/*db* ["SELECT * FROM c_instrumentanswers WHERE ParentId = ?" adm-id1]))))
        (is (= 1 (count (jdbc/query db/*db* ["SELECT * FROM c_instrumentanswers WHERE ParentId = ?" adm-id2]))))))))