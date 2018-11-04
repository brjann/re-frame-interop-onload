(ns bass4.test.external-messages
  (:require [clojure.test :refer :all]
            [bass4.test.core :refer [test-fixtures]]
            [clojure.core.async :refer [chan dropping-buffer <!!]]
            [bass4.email :as email]
            [clojure.tools.logging :as log]
            [bass4.external-messages :as external-messages]
            [clojure.string :as str]
            [bass4.sms-sender :as sms]))

(use-fixtures
  :once
  test-fixtures)

(deftest async-send-email-out
  (let [res (with-out-str (binding [email/*email-reroute*          :out
                                    external-messages/*debug-chan* (chan)]
                            (email/queue-email! "brjann@gmail.com" "XXX" "YYY")
                            (<!! external-messages/*debug-chan*)))]
    (is (str/includes? res "email"))
    (is (str/includes? res "brjann@gmail.com"))
    (is (str/includes? res "XXX"))
    (is (str/includes? res "YYY"))))

(deftest async-send-email-out
  (let [res (with-out-str (binding [sms/*sms-reroute*              :out
                                    external-messages/*debug-chan* (chan)]
                            (sms/queue-sms! "666" "ZZZ")
                            (<!! external-messages/*debug-chan*)))]
    (is (str/includes? res "SMS"))
    (is (str/includes? res "666"))
    (is (str/includes? res "ZZZ"))))