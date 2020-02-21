(ns bass4.test.assessment-activated-flagger
  (:require [clojure.test :refer :all]
            [bass4.assessment.activated-flagger :as activated-flagger]
            [bass4.test.assessment-utils :refer :all]
            [bass4.test.core :refer :all]
            [bass4.services.user :as user-service]
            [bass4.db.core :refer [*db*]]
            [bass4.assessment.db :as assessment-db]))

(use-fixtures
  :once
  test-fixtures)

(use-fixtures
  :each
  random-date-tz-fixture)

(deftest db-flag-participant-administration
  (let [user-id1       (user-service/create-user! project-ass1-id)
        user-id2       (user-service/create-user! project-ass1-id)
        user-id3       (user-service/create-user! project-ass1-id)
        user-id4       (user-service/create-user! project-ass1-id)
        assessment-id  (create-assessment! {"FlagParticipantWhenActivated" 1})
        assessment-id2 (create-assessment! {"FlagParticipantWhenActivated" 0})
        assessment-id3 (create-assessment! {"FlagParticipantWhenActivated" 1
                                            "ClinicianAssessment"          1})]
    (create-participant-administration!
      user-id1 assessment-id 1 {"Date" (midnight+d -5 *now*)})
    (create-participant-administration!
      user-id1 assessment-id2 1 {"Date" (midnight+d -5 *now*)})
    (create-participant-administration!
      user-id2 assessment-id 1 {"Date" (midnight *now*)})
    (create-participant-administration!
      user-id3 assessment-id 1 {"Date" (midnight+d +1 *now*)})
    (create-participant-administration!
      user-id4 assessment-id 1 {"Date"          (midnight+d -5 *now*)
                                "DateCompleted" 1})
    (create-participant-administration!
      user-id4 assessment-id3 1 {"Date" (midnight+d -5 *now*)})
    (is (= 3 (count (assessment-db/activated-flag-participant-administrations
                      *db*
                      *now*
                      *tz*
                      activated-flagger/flag-issuer
                      activated-flagger/oldest-allowed))))
    (is (= #{[user-id1 assessment-id 1]
             [user-id2 assessment-id 1]
             [user-id4 assessment-id3 1]}
           (flag-activated! *now*)))
    (is (= 0 (count (assessment-db/activated-flag-participant-administrations
                      *db*
                      *now*
                      *tz*
                      activated-flagger/flag-issuer
                      activated-flagger/oldest-allowed))))))

(deftest db-flag-group-administration
  (let [group-id1      (create-group!)
        group-id2      (create-group!)
        group-id3      (create-group!)
        user-id1       (user-service/create-user! project-ass1-id {:group group-id1})
        user-id4       (user-service/create-user! project-ass1-id {:group group-id1})
        _              (user-service/create-user! project-ass1-id {:group group-id2})
        user-id3       (user-service/create-user! project-ass1-id {:group group-id3})
        assessment-id  (create-assessment! {"Scope"                        1
                                            "FlagParticipantWhenActivated" 1})
        assessment-id2 (create-assessment! {"Scope"                        1
                                            "FlagParticipantWhenActivated" 0})
        assessment-id3 (create-assessment! {"Scope"                        1
                                            "FlagParticipantWhenActivated" 1
                                            "ClinicianAssessment"          1})]
    (create-group-administration!
      group-id1 assessment-id 1 {"Date" (midnight *now*)})
    (create-group-administration!
      group-id1 assessment-id2 1 {"Date" (midnight *now*)})
    (create-participant-administration!
      user-id1 assessment-id 1 {"Active" 0})
    (create-group-administration!
      group-id2 assessment-id 1 {"Date" (midnight+d +1 *now*)})
    (create-group-administration!
      group-id3 assessment-id 1 {"Date" (midnight+d -5 *now*)})
    (create-group-administration!
      group-id1 assessment-id3 1 {"Date" (midnight+d -5 *now*)})
    (is (= 4 (count (assessment-db/activated-flag-group-administrations
                      *db*
                      *now*
                      *tz*
                      activated-flagger/flag-issuer
                      activated-flagger/oldest-allowed))))
    (is (= #{[user-id1 assessment-id3 1]
             [user-id3 assessment-id 1]
             [user-id4 assessment-id 1]
             [user-id4 assessment-id3 1]}
           (flag-activated! *now*)))
    (is (= 0 (count (assessment-db/activated-flag-group-administrations
                      *db*
                      *now*
                      *tz*
                      activated-flagger/flag-issuer
                      activated-flagger/oldest-allowed))))))