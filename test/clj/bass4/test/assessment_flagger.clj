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

(def db-late-flag @#'assessment-flagger/db-late-flag-participant-administrations)

(deftest flag-participant-administration
  (let [group-id      (create-group!)
        user-id       (user-service/create-user! project-ass1-id {:group group-id})
        assessment-id (create-assessment! {"FlagParticipantWhenLate" 1
                                           "DayCountUntilLate"       50})]
    (create-participant-administration!
      user-id assessment-id 1 {:date (midnight+d -60 *now*)})
    (is (= 1 (count (db-late-flag *db* *now*))))))