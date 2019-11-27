(ns bass4.test.assessment-flagger
  (:require [clj-time.core :as t]
            [clojure.test :refer :all]
            [clojure.core.async :refer [chan go-loop <!]]
            [bass4.db.core :refer [*db*] :as db]
            [bass4.assessment.ongoing :as assessment-ongoing]
            [bass4.assessment.flagger :as assessment-flagger]
            [bass4.test.assessment-utils :refer :all]
            [bass4.test.core :refer :all]
            [bass4.services.user :as user-service]
            [bass4.services.bass :as bass]
            [bass4.time :as b-time]
            [clojure.tools.logging :as log]
            [bass4.utils :as utils]
            [bass4.services.bass :as bass-service]))

(use-fixtures
  :once
  test-fixtures)

(use-fixtures
  :each
  random-date-tz-fixture)

(def db-late-flag-participant @#'assessment-flagger/db-late-flag-participant-administrations)
(def db-late-flag-group @#'assessment-flagger/db-late-flag-group-administrations)

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
        now+1delay-1  (t/plus *now* (t/days (dec assessment-flagger/reflag-delay)))
        now+1delay    (t/plus *now* (t/days assessment-flagger/reflag-delay))
        now+2delay-1  (t/plus *now* (t/days (dec (* 2 assessment-flagger/reflag-delay))))
        now+2delay    (t/plus *now* (t/days (* 2 assessment-flagger/reflag-delay)))]
    (create-participant-administration!
      user-id1 assessment-id 1 {:date (midnight+d -5 *now*)})
    (create-participant-administration!
      user-id2 assessment-id 1 {:date (midnight+d -5 *now*)})
    (is (= #{[user-id1 assessment-id 1]
             [user-id2 assessment-id 1]}
           (flag!-flags-created *now* created-flags)))
    (is (= #{}
           (flag!-flags-created *now*)))
    (let [flag1-id (first (get @created-flags user-id1))]
      (bass-service/update-object-properties! "c_flag" flag1-id {"ClosedAt" (b-time/to-unix *now*)})
      (is (= #{}
             (flag!-flags-created *now*)))
      (is (= #{}
             (flag!-flags-created now+1delay-1)))
      (reset! created-flags {})
      (is (= #{[user-id1 assessment-id 1]}
             (flag!-flags-created
               (t/plus *now* (t/days assessment-flagger/reflag-delay))
               created-flags)))
      (is (= #{}
             (flag!-flags-created
               now+1delay)))
      (let [flag2-id (first (get @created-flags user-id1))]
        (bass-service/update-object-properties!
          "c_flag"
          flag2-id
          {"ClosedAt" (b-time/to-unix now+1delay)}))
      (is (zero? (count (db-late-flag-participant *db* now+2delay-1))))
      (is (= 1 (count (db-late-flag-participant *db* now+2delay))))
      (bass-service/update-object-properties!
        "c_flag"
        flag1-id
        {"ClosedAt" 0})
      (is (zero? (count (db-late-flag-participant *db* now+2delay)))))))

(deftest flag-participant-administration+reflag
  (let [created-flags (atom {})
        group         (create-group!)
        user-id1      (user-service/create-user! project-ass1-id {:group group})
        user-id2      (user-service/create-user! project-ass1-id {:group group})
        assessment-id (create-assessment! {"Scope"                   1
                                           "FlagParticipantWhenLate" 1
                                           "DayCountUntilLate"       5})
        now+1delay-1  (t/plus *now* (t/days (dec assessment-flagger/reflag-delay)))
        now+1delay    (t/plus *now* (t/days assessment-flagger/reflag-delay))
        now+2delay-1  (t/plus *now* (t/days (dec (* 2 assessment-flagger/reflag-delay))))
        now+2delay    (t/plus *now* (t/days (* 2 assessment-flagger/reflag-delay)))]
    (create-group-administration!
      group assessment-id 1 {:date (midnight+d -5 *now*)})
    (is (= #{[user-id1 assessment-id 1]
             [user-id2 assessment-id 1]}
           (flag!-flags-created *now* created-flags)))
    (is (= #{}
           (flag!-flags-created *now*)))
    (let [flag1-id (first (get @created-flags user-id1))]
      (bass-service/update-object-properties! "c_flag" flag1-id {"ClosedAt" (b-time/to-unix *now*)})
      (is (= #{}
             (flag!-flags-created *now*)))
      (is (= #{}
             (flag!-flags-created now+1delay-1)))
      (reset! created-flags {})
      (is (= #{[user-id1 assessment-id 1]}
             (flag!-flags-created
               (t/plus *now* (t/days assessment-flagger/reflag-delay))
               created-flags)))
      (is (= #{}
             (flag!-flags-created
               now+1delay)))
      (let [flag2-id (first (get @created-flags user-id1))]
        (bass-service/update-object-properties!
          "c_flag"
          flag2-id
          {"ClosedAt" (b-time/to-unix now+1delay)}))
      (is (zero? (count (db-late-flag-group *db* now+2delay-1))))
      (is (= 1 (count (db-late-flag-group *db* now+2delay))))
      (bass-service/update-object-properties!
        "c_flag"
        flag1-id
        {"ClosedAt" 0})
      (is (zero? (count (db-late-flag-group *db* now+2delay)))))))

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
    (log/debug (map :user-id (db-late-flag-group *db* *now*)))
    (is (= #{[user-id1 assessment-id 2]
             [user-id2 assessment-id 2]}
           (flag!-flags-created *now*)))))