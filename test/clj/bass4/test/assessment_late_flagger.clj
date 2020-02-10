(ns bass4.test.assessment-late-flagger
  (:require [clj-time.core :as t]
            [clojure.test :refer :all]
            [clojure.core.async :refer [chan go-loop <!]]
            [bass4.assessment.late-flagger :as late-flagger]
            [bass4.test.assessment-utils :refer :all]
            [bass4.test.core :refer :all]
            [bass4.services.user :as user-service]
            [bass4.services.bass :as bass]
            [bass4.time :as b-time]
            [bass4.services.bass :as bass-service]
            [clojure.java.jdbc :as jdbc]
            [bass4.db.core :refer [*db*]]
            [bass4.assessment.administration :as administration]
            [bass4.db.orm-classes :as orm]))

(use-fixtures
  :once
  test-fixtures)

(use-fixtures
  :each
  random-date-tz-fixture)

(def db-late-flag-participant @#'late-flagger/db-participant-administrations)
(def db-late-flag-group @#'late-flagger/db-group-administrations)

(deftest db-flag-participant-administration
  (let [user-id1      (user-service/create-user! project-ass1-id)
        user-id2      (user-service/create-user! project-ass1-id)
        user-id3      (user-service/create-user! project-ass1-id)
        assessment-id (create-assessment! {"FlagParticipantWhenLate" 1
                                           "DayCountUntilLate"       5})]
    (create-participant-administration!
      user-id1 assessment-id 1 {"Date" (midnight+d -5 *now*)})
    (create-participant-administration!
      user-id2 assessment-id 1 {"Date" (midnight+d -5 *now*)})
    (create-participant-administration!
      user-id3 assessment-id 1 {"Date"          (midnight+d -5 *now*)
                                "DateCompleted" 1})
    (is (= 2 (count (db-late-flag-participant *db* *now*))))))

(deftest db-flag-participant-administration-completed
  (let [created-flags     (atom {})
        user-id1          (user-service/create-user! project-ass1-id)
        assessment-id     (create-assessment! {"FlagParticipantWhenLate" 1
                                               "DayCountUntilLate"       5})
        administration-id (create-participant-administration!
                            user-id1 assessment-id 1 {"Date" (midnight+d -5 *now*)})]
    (is (= #{[user-id1 assessment-id 1]}
           (flag-late! *now* created-flags 1)))
    (administration/set-administrations-completed! user-id1 [administration-id])
    (is (< 0 (-> (jdbc/query *db* ["SELECT ClosedAt FROM c_flag WHERE ObjectId = ?" (-> @created-flags
                                                                                        (vals)
                                                                                        (ffirst))])
                 (first)
                 (vals)
                 (first))))))

(deftest db-flag-clinician-assessment
  (let [user-id1      (user-service/create-user! project-ass1-id)
        assessment-id (create-assessment! {"Scope"                   0
                                           "FlagParticipantWhenLate" 1
                                           "DayCountUntilLate"       5
                                           "ClinicianAssessment"     1})]
    (create-participant-administration!
      user-id1 assessment-id 1 {:date (midnight+d -5 *now*)})
    (is (= 1 (count (db-late-flag-participant *db* *now*))))))

(deftest db-flag-group-administration
  (let [group-id1     (create-group!)
        group-id2     (create-group!)
        user-id1      (user-service/create-user! project-ass1-id {:group group-id1})
        user-id2      (user-service/create-user! project-ass1-id {:group group-id1})
        user-id3      (user-service/create-user! project-ass1-id {:group group-id2})
        assessment-id (create-assessment! {"Scope"                   1
                                           "FlagParticipantWhenLate" 1
                                           "DayCountUntilLate"       5})]
    (create-group-administration!
      group-id1 assessment-id 1 {:date (midnight+d -5 *now*)})
    (create-group-administration!
      group-id2 assessment-id 1 {:date (midnight+d -5 *now*)})
    (create-participant-administration!
      user-id2 assessment-id 1 {"DateCompleted" 1})
    (is (= 2 (count (db-late-flag-group *db* *now*))))))

(deftest flag-participant-administration+reflag
  (let [created-flags (atom {})
        user-id1      (user-service/create-user! project-ass1-id)
        user-id2      (user-service/create-user! project-ass1-id)
        assessment-id (create-assessment! {"Scope"                   0
                                           "FlagParticipantWhenLate" 1
                                           "DayCountUntilLate"       5})
        now+1delay-1  (t/plus *now* (t/days (dec late-flagger/reflag-delay)))
        now+1delay    (t/plus *now* (t/days late-flagger/reflag-delay))
        now+2delay-1  (t/plus *now* (t/days (dec (* 2 late-flagger/reflag-delay))))
        now+2delay    (t/plus *now* (t/days (* 2 late-flagger/reflag-delay)))]
    (create-participant-administration!
      user-id1 assessment-id 1 {:date (midnight+d -5 *now*)})
    (create-participant-administration!
      user-id2 assessment-id 1 {:date (midnight+d -5 *now*)})
    (is (= #{[user-id1 assessment-id 1]
             [user-id2 assessment-id 1]}
           (flag-late! *now* created-flags 2)))
    (is (= #{} (flag-late! *now*)))
    (let [flag1-id (first (get @created-flags user-id1))]
      (orm/update-object-properties! "c_flag" flag1-id {"ClosedAt" (b-time/to-unix *now*)})
      (is (= #{} (flag-late! *now*)))
      (is (= #{} (flag-late! now+1delay-1)))
      (reset! created-flags {})
      (is (= #{[user-id1 assessment-id 1]}
             (flag-late! now+1delay
                         created-flags
                         1)))
      (is (= 1 (flag-comment-count flag1-id)))
      (is (= #{} (flag-late! now+1delay)))
      (orm/update-object-properties! "c_flag"
                                     flag1-id
                                     {"ClosedAt" (b-time/to-unix now+1delay)})
      (is (zero? (count (db-late-flag-participant *db* now+2delay-1))))
      (is (= 1 (count (db-late-flag-participant *db* now+2delay))))
      (orm/update-object-properties! "c_flag"
                                     flag1-id
                                     {"ReflagDelay" (dec late-flagger/reflag-delay)})
      (is (= 1 (count (flag-late! now+2delay-1))))
      (is (= 2 (flag-comment-count flag1-id))))))

(deftest flag-group-administration+reflag
  (let [created-flags (atom {})
        group         (create-group!)
        user-id1      (user-service/create-user! project-ass1-id {:group group})
        user-id2      (user-service/create-user! project-ass1-id {:group group})
        assessment-id (create-assessment! {"Scope"                   1
                                           "FlagParticipantWhenLate" 1
                                           "DayCountUntilLate"       5})
        now+1delay-1  (t/plus *now* (t/days (dec late-flagger/reflag-delay)))
        now+1delay    (t/plus *now* (t/days late-flagger/reflag-delay))
        now+2delay-1  (t/plus *now* (t/days (dec (* 2 late-flagger/reflag-delay))))
        now+2delay    (t/plus *now* (t/days (* 2 late-flagger/reflag-delay)))]
    (create-group-administration!
      group assessment-id 1 {:date (midnight+d -5 *now*)})
    (is (= #{[user-id1 assessment-id 1]
             [user-id2 assessment-id 1]}
           (flag-late! *now* created-flags 2)))
    (is (= #{} (flag-late! *now*)))
    (let [flag1-id (first (get @created-flags user-id1))]
      (orm/update-object-properties! "c_flag"
                                     flag1-id
                                     {"ClosedAt"
                                      (b-time/to-unix *now*)})
      (is (= #{} (flag-late! *now*)))
      (is (= #{} (flag-late! now+1delay-1)))
      (reset! created-flags {})
      (is (= #{[user-id1 assessment-id 1]}
             (flag-late! now+1delay
                         created-flags
                         1)))
      (is (= 1 (flag-comment-count flag1-id)))
      (is (= #{} (flag-late! now+1delay)))
      (let [flag2-id (first (get @created-flags user-id1))]
        (orm/update-object-properties! "c_flag"
                                       flag2-id
                                       {"ClosedAt" (b-time/to-unix now+1delay)}))
      (is (zero? (count (db-late-flag-group *db* now+2delay-1))))
      (is (= 1 (count (db-late-flag-group *db* now+2delay))))
      (orm/update-object-properties! "c_flag"
                                     flag1-id
                                     {"ReflagDelay" (dec late-flagger/reflag-delay)})
      (is (= 1 (count (flag-late! now+2delay-1))))
      (is (= 2 (flag-comment-count flag1-id))))))

(deftest flag-participant-administration+no+reflag
  (let [created-flags (atom {})
        user-id1      (user-service/create-user! project-ass1-id)
        user-id2      (user-service/create-user! project-ass1-id)
        assessment-id (create-assessment! {"Scope"                   0
                                           "FlagParticipantWhenLate" 1
                                           "DayCountUntilLate"       5})
        now+1delay-1  (t/plus *now* (t/days (dec late-flagger/reflag-delay)))
        now+1delay    (t/plus *now* (t/days late-flagger/reflag-delay))
        now+2delay-1  (t/plus *now* (t/days (dec (* 2 late-flagger/reflag-delay))))
        now+2delay    (t/plus *now* (t/days (* 2 late-flagger/reflag-delay)))]
    (create-participant-administration!
      user-id1 assessment-id 1 {:date (midnight+d -5 *now*)})
    (is (= #{[user-id1 assessment-id 1]}
           (flag-late! *now* created-flags 1)))
    (let [flag1-id (first (get @created-flags user-id1))]
      (bass-service/update-object-properties! "c_flag" flag1-id {"ReflagDelay" 0
                                                                 "ClosedAt"    (b-time/to-unix *now*)})
      (is (= #{} (flag-late! now+1delay))))))

(deftest flag-group-administration+no-reflag
  (let [created-flags (atom {})
        group         (create-group!)
        user-id1      (user-service/create-user! project-ass1-id {:group group})
        user-id2      (user-service/create-user! project-ass1-id {:group group})
        assessment-id (create-assessment! {"Scope"                   1
                                           "FlagParticipantWhenLate" 1
                                           "DayCountUntilLate"       5})
        now+1delay-1  (t/plus *now* (t/days (dec late-flagger/reflag-delay)))
        now+1delay    (t/plus *now* (t/days late-flagger/reflag-delay))
        now+2delay-1  (t/plus *now* (t/days (dec (* 2 late-flagger/reflag-delay))))
        now+2delay    (t/plus *now* (t/days (* 2 late-flagger/reflag-delay)))]
    (create-group-administration!
      group assessment-id 1 {:date (midnight+d -5 *now*)})
    (is (= #{[user-id1 assessment-id 1]
             [user-id2 assessment-id 1]}
           (flag-late! *now* created-flags 2)))
    (is (= #{} (flag-late! *now*)))
    (let [flag1-id (first (get @created-flags user-id1))]
      (bass-service/update-object-properties! "c_flag"
                                              flag1-id
                                              {"ClosedAt"    (b-time/to-unix *now*)
                                               "ReflagDelay" 0})
      (is (= #{} (flag-late! now+1delay))))))

(deftest db-flag-manual-group-administration
  (let [group-id1     (create-group!)
        user-id1      (user-service/create-user! project-ass1-id {:group group-id1})
        user-id2      (user-service/create-user! project-ass1-id {:group group-id1})
        assessment-id (create-assessment! {"RepetitionType"          2
                                           "Repetitions"             2
                                           "Scope"                   1
                                           "FlagParticipantWhenLate" 1
                                           "DayCountUntilLate"       5})]
    (create-group-administration!
      group-id1 assessment-id 1 {:date (midnight+d -10 *now*)})
    (create-group-administration!
      group-id1 assessment-id 2 {:date (midnight+d -5 *now*)})
    (is (= #{[user-id1 assessment-id 2]
             [user-id2 assessment-id 2]}
           (flag-late! *now*)))))

(deftest flag-participant-administration+deflag
  (let [created-flags (atom {})
        user-id1      (user-service/create-user! project-ass1-id)
        user-id2      (user-service/create-user! project-ass1-id)
        assessment-id (create-assessment! {"Scope"                   0
                                           "FlagParticipantWhenLate" 1
                                           "DayCountUntilLate"       5
                                           "TimeLimit"               20})]
    (create-participant-administration!
      user-id1 assessment-id 1 {:date (midnight+d -6 *now*)})
    (create-participant-administration!
      user-id2 assessment-id 1 {:date (midnight+d -5 *now*)})
    (is (= #{[user-id1 assessment-id 1]
             [user-id2 assessment-id 1]}
           (flag-late! *now* created-flags 2)))
    (is (= 0 (count (late-flagger/deflag-assessments! *db* *now*))))
    (is (= 1 (count (late-flagger/deflag-assessments! *db* (t/plus *now* (t/days 14))))))
    (is (= 0 (count (late-flagger/deflag-assessments! *db* (t/plus *now* (t/days 14))))))
    (is (= 1 (count (late-flagger/deflag-assessments! *db* (t/plus *now* (t/days 15))))))
    (is (= 0 (count (late-flagger/deflag-assessments! *db* (t/plus *now* (t/days 15))))))))

(deftest flag-participant-administration+deflag-clinician-assessment
  (let [created-flags (atom {})
        user-id1      (user-service/create-user! project-ass1-id)
        user-id2      (user-service/create-user! project-ass1-id)
        assessment-id (create-assessment! {"Scope"                   0
                                           "FlagParticipantWhenLate" 1
                                           "DayCountUntilLate"       5
                                           "TimeLimit"               20
                                           "ClinicianAssessment"     1})]
    (create-participant-administration!
      user-id1 assessment-id 1 {:date (midnight+d -6 *now*)})
    (is (= #{[user-id1 assessment-id 1]}
           (flag-late! *now*)))
    (is (= 1 (count (late-flagger/deflag-assessments! *db* (t/plus *now* (t/days 14))))))))

(deftest flag-participant-administration+deflag-inactive
  (let [created-flags     (atom {})
        user-id1          (user-service/create-user! project-ass1-id)
        assessment-id     (create-assessment! {"Scope"                   0
                                               "FlagParticipantWhenLate" 1
                                               "DayCountUntilLate"       5
                                               "TimeLimit"               20})
        administration-id (create-participant-administration!
                            user-id1 assessment-id 1 {:date (midnight+d -6 *now*)})]
    (is (= #{[user-id1 assessment-id 1]}
           (flag-late! *now* created-flags 1)))
    (is (= 0 (count (late-flagger/deflag-assessments! *db* *now*))))
    (orm/update-object-properties*! *db*
                                    "c_participantadministration"
                                    administration-id
                                    {"active" 0})
    (is (= 1 (count (late-flagger/deflag-assessments! *db* *now*))))
    (orm/update-object-properties*! *db*
                                    "c_participantadministration"
                                    administration-id
                                    {"active" 1})
    (is (= #{}
           (flag-late! (t/plus *now* (t/days (dec late-flagger/reflag-delay))))))
    (is (= #{[user-id1 assessment-id 1]}
           (flag-late! (t/plus *now* (t/days late-flagger/reflag-delay)))))))