(ns bass4.test.assessment-flagger
  (:require [clj-time.core :as t]
            [clojure.test :refer :all]
            [bass4.db.core :refer [*db*] :as db]
            [bass4.assessment.ongoing :as assessment-ongoing]
            [bass4.assessment.flagger :as assessment-flagger]
            [bass4.test.assessment-utils :refer :all]
            [bass4.test.core :refer :all]
            [bass4.services.user :as user-service]
            [bass4.services.bass :as bass]
            [bass4.time :as b-time]))

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
        assessment-id (create-assessment! {"FlagParticipantWhenLate" 1
                                           "DayCountUntilLate"       5})]
    (create-participant-administration!
      user-id1 assessment-id 1 {"Date" (midnight+d -5 *now*)})
    (create-participant-administration!
      user-id2 assessment-id 1 {"Date"          (midnight+d -5 *now*)
                                "DateCompleted" 1})
    (is (= 1 (count (db-late-flag-participant *db* *now*))))))

(deftest db-flag-group-administration
  (let [group-id      (create-group!)
        user-id1      (user-service/create-user! project-ass1-id {:group group-id})
        user-id2      (user-service/create-user! project-ass1-id {:group group-id})
        assessment-id (create-assessment! {"Scope"                   1
                                           "FlagParticipantWhenLate" 1
                                           "DayCountUntilLate"       5})]
    (create-group-administration!
      group-id assessment-id 1 {:date (midnight+d -5 *now*)})
    (create-participant-administration!
      user-id2 assessment-id 1 {"DateCompleted" 1})
    (is (= 1 (count (db-late-flag-group *db* *now*))))))