(ns bass4.test.assessments
  (:require [clj-time.core :as t]
            [bass4.db.core :refer [*db*] :as db]
            [bass4.services.assessments :as assessments]
            [bass4.test.utils :refer [get-edn] :as test-utils]
            [clojure.test :refer :all]))

(use-fixtures
  :once
  test-utils/test-fixtures)

(defn get-ass-1-pending
  []
  (with-redefs [t/now (constantly (t/date-time 2017 05 30 17 16 00))
                db/get-user-group (constantly {:group-name nil :group-id nil})
                db/get-user-assessment-series (constantly {:assessment-series-id 535756})
                db/get-assessment-series-assessments (constantly (get-edn "ass-1-series-ass"))
                db/get-user-administrations (constantly (get-edn "ass-1-adms"))]
    (assessments/get-pending-assessments 535795)))

(defn get-ass-1-rounds
  [pending]
  (with-redefs [t/now (constantly (t/date-time 1986 10 14))]
    (doall (assessments/generate-assessment-round 234 pending))))

(deftest ass-1
         (let [pending (get-ass-1-pending)]
           (is (= (get-edn "ass-1-res") pending))
           (is (= (get-ass-1-rounds pending)
                  (get-edn "ass-1-rounds")))))

