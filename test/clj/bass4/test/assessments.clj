(ns bass4.test.assessments
  (:require [clj-time.core :as t]
            [bass4.db.core :refer [*db*] :as db]
            [bass4.services.assessments :as assessments]
            [bass4.test.core :refer [get-edn test-fixtures]]
            [clojure.test :refer :all]))

(use-fixtures
  :once
  test-fixtures)

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

(defn get-ass-3-pending
  []
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))
                db/get-user-group (constantly {:group-name nil :group-id nil})
                db/get-user-assessment-series (constantly {:assessment-series-id 535756})
                db/get-assessment-series-assessments (constantly (get-edn "ass-3-series-ass"))
                db/get-user-administrations (constantly (get-edn "ass-3-adms"))
                db/get-assessments-instruments (constantly (get-edn "ass-3-instruments"))
                db/get-administration-completed-instruments (constantly ())
                db/get-administration-additional-instruments (constantly ())]
    (assessments/get-pending-assessments 535899)))

(defn get-ass-3-rounds
  [pending]
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (doall (assessments/generate-assessment-round 535899 pending))))

(deftest ass-3
  (let [pending (get-ass-3-pending)]
    (is (= (get-edn "ass-3-res") pending))
    (is (= (get-ass-3-rounds pending)
           (get-edn "ass-3-rounds")))))

(defn get-ass-4-pending
  []
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))
                db/get-user-group (constantly {:group-name nil :group-id nil})
                db/get-user-assessment-series (constantly {:assessment-series-id 535756})
                db/get-assessment-series-assessments (constantly (get-edn "ass-4-series-ass"))
                db/get-user-administrations (constantly (get-edn "ass-4-adms"))
                db/get-assessments-instruments (constantly (get-edn "ass-4-instruments"))
                db/get-administration-completed-instruments (constantly ())
                db/get-administration-additional-instruments (constantly ())]
    (assessments/get-pending-assessments 536048)))

(defn get-ass-4-rounds
  [pending]
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (doall (assessments/generate-assessment-round 536048 pending))))

(deftest ass-4
  (let [pending (get-ass-4-pending)]
    (is (= (get-edn "ass-4-res") pending))
    (is (= (get-ass-4-rounds pending)
           (get-edn "ass-4-rounds")))))

(defn get-ass-5-pending
  []
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))
                db/get-user-group (constantly {:group-name nil :group-id nil})
                db/get-user-assessment-series (constantly {:assessment-series-id 535756})
                db/get-assessment-series-assessments (constantly (get-edn "ass-5-series-ass"))
                db/get-user-administrations (constantly (get-edn "ass-5-adms"))
                db/get-assessments-instruments (constantly (get-edn "ass-5-instruments"))
                db/get-administration-completed-instruments (constantly '({:administration-id 536049, :instrument-id 286}))
                db/get-administration-additional-instruments (constantly ())]
    (assessments/get-pending-assessments 536048)))

(defn get-ass-5-rounds
  [pending]
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (doall (assessments/generate-assessment-round 536048 pending))))

(deftest ass-5
  (let [pending (get-ass-5-pending)]
    (is (= (get-edn "ass-5-res") pending))
    (is (= (get-ass-5-rounds pending)
           (get-edn "ass-5-rounds")))))

(defn get-ass-6-pending
  []
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (assessments/get-pending-assessments 536124)))

(defn get-ass-6-rounds
  [pending]
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (doall (assessments/generate-assessment-round 536124 pending))))

(deftest ass-6
  (let [pending (get-ass-6-pending)]
    (is (= (get-edn "ass-6-res") pending))
    (is (= (get-ass-6-rounds pending)
           (get-edn "ass-6-rounds")))))

(defn get-ass-7-pending
  []
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (assessments/get-pending-assessments 536140)))

(defn get-ass-7-rounds
  [pending]
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (doall (assessments/generate-assessment-round 536140 pending))))

(deftest ass-7
  (let [pending (get-ass-7-pending)]
    (is (= (get-edn "ass-7-res") pending))
    (is (= (get-ass-7-rounds pending)
           (get-edn "ass-7-rounds")))))