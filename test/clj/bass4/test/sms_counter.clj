(ns bass4.test.sms-counter
  (:require [clojure.test :refer :all]
            [bass4.test.core :refer :all]
            [bass4.external-messages.sms-counter :as sms-counter]
            [bass4.external-messages.sms-sender :as sms]
            [bass4.db.core :as db]))

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

(deftest count-sms
  (fix-time
    (binding [sms-counter/counter (atom [])
              sms/*sms-reroute*   :void]
      (sms/send-sms-now! db/*db* "070-7176562" "Satan")
      (is (= 1 (sms-counter/count)))
      (advance-time-s! 1)
      (sms/async-sms! db/*db* "070-7176562" "Satan")
      (is (= 2 (sms-counter/count)))
      (advance-time-s! (dec sms-counter/window))
      (is (= 1 (sms-counter/count)))
      (advance-time-s! 1)
      (is (= 0 (sms-counter/count))))))