(ns bass4.test.sms-counter
  (:require [clojure.test :refer :all]
            [bass4.test.core :refer :all]
            [bass4.external-messages.sms-counter :as sms-counter]))

(deftest counter
  (fix-time
    (binding [sms-counter/counter (atom [])]
      (sms-counter/inc!)
      (is (= 1 (sms-counter/count)))
      (advance-time-s! 1)
      (sms-counter/inc!)
      (is (= 2 (sms-counter/count)))
      (advance-time-s! (dec sms-counter/window))
      (is (= 1 (sms-counter/count)))
      (advance-time-s! 1)
      (is (= 0 (sms-counter/count))))))