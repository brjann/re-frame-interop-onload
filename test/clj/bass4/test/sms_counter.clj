(ns bass4.test.sms-counter
  (:require [clojure.test :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [clojure.core.async :refer [<!! chan poll! timeout alts!!]]
            [bass4.test.core :refer :all]
            [bass4.external-messages.sms-counter :as sms-counter]
            [bass4.external-messages.sms-sender :as sms]
            [bass4.db.core :as db]
            [bass4.middleware.lockdown :as lockdown]
            [bass4.external-messages.email-sender :as email]
            [clojure.string :as str]))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

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
      (<!! (sms/async-sms! db/*db* "070-7176562" "Satan"))
      (is (= 2 (sms-counter/count)))
      (advance-time-s! (dec sms-counter/window))
      (is (= 1 (sms-counter/count)))
      (advance-time-s! 1)
      (is (= 0 (sms-counter/count))))))

(deftest lockdown
  (fix-time
    (let [c         (chan 2)
          too-many  lockdown/too-many
          half-many (/ too-many 2)]
      (when-not (even? (int too-many))
        (throw (Exception. "Too many sms must be even! " too-many)))
      (binding [sms-counter/counter   (atom [])
                lockdown/last-send    (atom nil)
                lockdown/locked-down? (atom false)
                sms/*sms-reroute*     c
                email/*email-reroute* c]
        (doall (repeatedly (dec half-many) sms-counter/inc!))
        (visit *s* "/login")
        (is (nil? (poll! c)))
        (sms-counter/inc!)
        (visit *s* "/login")
        (let [[[_ m1] _] (alts!! [c (timeout 5000)])
              [[_ m2] _] (alts!! [c (timeout 5000)])]
          (is (str/includes? m1 (str half-many)))
          (is (str/includes? m2 (str half-many))))
        (advance-time-h! lockdown/send-interval)
        (visit *s* "/login")
        (is (nil? (poll! c)))
        (advance-time-s! 1)
        (visit *s* "/login")
        (let [[[_ m1] _] (alts!! [c (timeout 5000)])
              [[_ m2] _] (alts!! [c (timeout 5000)])]
          (is (str/includes? m1 (str (inc half-many))))
          (is (str/includes? m2 (str (inc half-many)))))
        (advance-time-s! sms-counter/window)
        (is (= 0 (sms-counter/count)))
        (doall (repeatedly too-many sms-counter/inc!))
        (has (visit *s* "/login") (status? 503))
        (is @lockdown/locked-down?)
        (let [[[_ m1] _] (alts!! [c (timeout 5000)])
              [[_ m2] _] (alts!! [c (timeout 5000)])]
          (is (str/includes? m1 "LOCK"))
          (is (str/includes? m2 "LOCK")))
        (advance-time-s! sms-counter/window)
        (has (visit *s* "/login") (status? 503))
        (has (visit *s* "/embedded") (status? 403))))))