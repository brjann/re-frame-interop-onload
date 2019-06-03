(ns bass4.test.assessment-edn
  (:require [clj-time.core :as t]
            [bass4.db.core :refer [*db*] :as db]
            [bass4.assessment.ongoing :as assessment-ongoing]
            [bass4.assessment.administration :as administration]
            [bass4.test.core :refer :all]
            [clojure.test :refer :all]))

(use-fixtures
  :once
  test-fixtures)

(def x 8)

(defn get-ass-1-pending
  []
  (with-redefs [t/now                         (constantly (t/date-time 2017 05 30 17 16 00))
                db/get-user-group             (constantly {:group-name nil :group-id nil})
                db/get-user-assessment-series (constantly {:assessment-series-id 535756})
                db/get-user-assessments       (constantly (get-edn "ass-1-series-ass"))
                db/get-user-administrations   (constantly (get-edn "ass-1-adms"))]
    (assessment-ongoing/ongoing-assessments 535795)))

(defn get-ass-1-rounds
  [pending]
  (with-redefs [t/now (constantly (t/date-time 1986 10 14))]
    (doall (administration/generate-assessment-round 234 pending))))

(deftest ass-1
  (let [pending (get-ass-1-pending)]
    (is (= (get-ass-1-rounds pending)
           (get-edn "ass-1-rounds")))))

(defn get-ass-3-pending
  []
  (with-redefs [t/now                                        (constantly (t/date-time 2017 06 12 9 40 0))
                db/get-user-group                            (constantly {:group-name nil :group-id nil})
                db/get-user-assessment-series                (constantly {:assessment-series-id 535756})
                db/get-user-assessments                      (constantly (get-edn "ass-3-series-ass"))
                db/get-user-administrations                  (constantly (get-edn "ass-3-adms"))
                db/get-assessments-instruments               (constantly (get-edn "ass-3-instruments"))
                db/get-administration-completed-instruments  (constantly ())
                db/get-administration-additional-instruments (constantly ())]
    (assessment-ongoing/ongoing-assessments 535899)))

(defn get-ass-3-rounds
  [pending]
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (doall (administration/generate-assessment-round 535899 pending))))

(deftest ass-3
  (let [pending (get-ass-3-pending)]
    (is (= (get-edn "ass-3-rounds")
           (get-ass-3-rounds pending)))))

(defn get-ass-4-pending
  []
  (with-redefs [t/now                                        (constantly (t/date-time 2017 06 12 9 40 0))
                db/get-user-group                            (constantly {:group-name nil :group-id nil})
                db/get-user-assessment-series                (constantly {:assessment-series-id 535756})
                db/get-user-assessments                      (constantly (get-edn "ass-4-series-ass"))
                db/get-user-administrations                  (constantly (get-edn "ass-4-adms"))
                db/get-assessments-instruments               (constantly (get-edn "ass-4-instruments"))
                db/get-administration-completed-instruments  (constantly ())
                db/get-administration-additional-instruments (constantly ())]
    (assessment-ongoing/ongoing-assessments 536048)))

(defn get-ass-4-rounds
  [pending]
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (doall (administration/generate-assessment-round 536048 pending))))

(deftest ass-4
  (let [pending (get-ass-4-pending)]
    (is (= (get-ass-4-rounds pending)
           (get-edn "ass-4-rounds")))))

(defn get-ass-5-pending
  []
  (with-redefs [t/now                                        (constantly (t/date-time 2017 06 12 9 40 0))
                db/get-user-group                            (constantly {:group-name nil :group-id nil})
                db/get-user-assessment-series                (constantly {:assessment-series-id 535756})
                db/get-user-assessments                      (constantly (get-edn "ass-5-series-ass"))
                db/get-user-administrations                  (constantly (get-edn "ass-5-adms"))
                db/get-assessments-instruments               (constantly (get-edn "ass-5-instruments"))
                db/get-administration-completed-instruments  (constantly '({:administration-id 536049, :instrument-id 286}))
                db/get-administration-additional-instruments (constantly ())]
    (assessment-ongoing/ongoing-assessments 536048)))

(defn get-ass-5-rounds
  [pending]
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (doall (administration/generate-assessment-round 536048 pending))))

(deftest ass-5
  (let [pending (get-ass-5-pending)]
    (is (= (get-ass-5-rounds pending)
           (get-edn "ass-5-rounds")))))

(defn get-ass-6-pending
  []
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (sort-by :assessment-id (assessment-ongoing/ongoing-assessments 536124))))

(defn get-ass-6-rounds
  [pending]
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (doall (administration/generate-assessment-round 536124 pending))))

(deftest ass-6-group
  (let [pending (get-ass-6-pending)]
    (is (= (get-ass-6-rounds pending)
           (get-edn "ass-6-rounds")))))

(defn get-ass-7-pending
  []
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (sort-by :assessment-id (assessment-ongoing/ongoing-assessments 536140))))

(defn get-ass-7-rounds
  [pending]
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (doall (administration/generate-assessment-round 536140 pending))))

(deftest ass-7-group-some-done
  (let [pending (get-ass-7-pending)]
    (is (= (get-ass-7-rounds pending)
           (get-edn "ass-7-rounds")))))

(defn get-ass-8-pending
  []
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 12 33 0))]
    (assessment-ongoing/ongoing-assessments 535899)))

(defn get-ass-8-rounds
  [pending]
  (with-redefs [t/now (constantly (t/date-time 2017 06 12 9 40 0))]
    (doall (administration/generate-assessment-round 535899 pending))))

(deftest ass-8-no-text
  (let [pending (get-ass-8-pending)]
    (is (= (get-edn "ass-8-rounds")
           (get-ass-8-rounds pending)))))