(ns bass4.test.assessment-reminder-sorting
  (:require [bass4.assessment.reminder :as assessment-reminder]
            [bass4.test.core :refer :all]
            [clojure.test :refer :all]))

(def ac @#'assessment-reminder/assessment-comparator)

(deftest test-comparator
  (is (= -1 (ac {:remind-message "{QUICKURL}"}
                {:remind-message "XX"})))
  (is (= 1 (ac {:remind-message "XX"}
               {:remind-message "{QUICKURL}"})))

  (is (= -1 (ac {:remind-message "X"}
                {:remind-message ""})))
  (is (= 1 (ac {:remind-message ""}
               {:remind-message "X"})))
  (is (= -1 (ac {:priority 0}
                {:priority 1})))
  (is (= 1 (ac {:priority 1}
               {:priority 0})))
  (is (= 1 (ac {:sort-order 1}
               {:sort-order 0})))
  (is (= -1 (ac {:sort-order 0}
                {:sort-order 1})))

  (is (= -1 (ac {:remind-message "{QUICKURL}"
                 :priority       1}
                {:remind-message "XX"
                 :priority       0})))
  (is (= 1 (ac {:remind-message "{QUICKURL}XX"
                :priority       1}
               {:remind-message "{QUICKURL}"
                :priority       0})))


  (is (= -1 (ac {:remind-message ""
                 :priority       0}
                {:remind-message ""
                 :priority       1})))
  (is (= -1 (ac {:remind-message "X"
                 :priority       1}
                {:remind-message ""
                 :priority       0})))
  (is (= 1 (ac {:priority   1
                :sort-order 1}
               {:priority   1
                :sort-order 0})))
  (is (= -1 (ac {:priority   0
                 :sort-order 1}
                {:priority   1
                 :sort-order 0}))))

