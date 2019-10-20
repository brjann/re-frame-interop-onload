(ns bass4.test.assessment-edn
  (:require [clj-time.core :as t]
            [bass4.assessment.ongoing :as assessment-ongoing]
            [bass4.assessment.administration :as administration]
            [bass4.test.core :refer :all]
            [clojure.test :refer :all]))

(use-fixtures
  :once
  test-fixtures)

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