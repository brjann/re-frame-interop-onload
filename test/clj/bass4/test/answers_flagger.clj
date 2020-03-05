(ns bass4.test.answers-flagger
  (:require [clojure.test :refer :all]
            [bass4.instrument.flagger :as answers-flagger]))

(def parse-spec @#'answers-flagger/parse-spec)
(def eval-condition @#'answers-flagger/eval-condition)

(deftest parse-spec-test
  (is (= {:instrument "123"
          :condition  "@8==10"
          :msg        nil})
      (parse-spec " 123 : @8==10:"))
  (is (= {:instrument "123"
          :condition  "@8==10"
          :msg        "hejsan"})
      (parse-spec " 123 : @8==10: hejsan"))
  (is (= {:instrument "123"
          :condition  "@8==10"
          :msg        "hejsan: hoppsan"})
      (parse-spec " 123 : @8==10: hejsan: hoppsan")))

(deftest eval-condition-test
  (is (= 1 (eval-condition "@8==10" {"@8" 10})))
  (is (= 0 (eval-condition "@8==10" {"@8" 11})))
  (is (= 1 (eval-condition "@8==10&&sum==2" {"@8" 10 "sum" 2})))
  (is (= 0 (eval-condition "@8==10&&sum==2" {"@8" 10 "sum" 3})))
  (is (= 1 (eval-condition "@8==10||sum==2" {"@8" 11 "sum" 2e}))))