(ns bass4.test.assessment-activated-flagger
  (:require [clj-time.core :as t]
            [clojure.test :refer :all]
            [clojure.core.async :refer [chan go-loop <!]]
            [bass4.assessment.activated-flagger :as activated-flagger]
            [bass4.test.assessment-utils :refer :all]
            [bass4.test.core :refer :all]
            [bass4.services.user :as user-service]
            [bass4.services.bass :as bass]
            [bass4.time :as b-time]
            [bass4.services.bass :as bass-service]
            [clojure.java.jdbc :as jdbc]
            [bass4.db.core :refer [*db*]]
            [bass4.assessment.administration :as administration]
            [clojure.tools.logging :as log]))

(use-fixtures
  :once
  test-fixtures)

(use-fixtures
  :each
  random-date-tz-fixture)

(def db-activated-flag-participant @#'activated-flagger/db-participant-administrations)
(def db-activated-flag-group @#'activated-flagger/db-group-administrations)

(deftest db-flag-participant-administration
  (let [user-id1      (user-service/create-user! project-ass1-id)
        user-id2      (user-service/create-user! project-ass1-id)
        user-id3      (user-service/create-user! project-ass1-id)
        user-id4      (user-service/create-user! project-ass1-id)
        assessment-id (create-assessment! {"FlagParticipantWhenActivated" 1})]
    (create-participant-administration!
      user-id1 assessment-id 1 {"Date" (midnight+d -5 *now*)})
    (create-participant-administration!
      user-id2 assessment-id 1 {"Date" (midnight *now*)})
    (create-participant-administration!
      user-id3 assessment-id 1 {"Date" (midnight+d +1 *now*)})
    (create-participant-administration!
      user-id4 assessment-id 1 {"Date"          (midnight+d -5 *now*)
                                "DateCompleted" 1})
    (is (= 2 (count (db-activated-flag-participant *db* *now* *tz*))))))