(ns bass4.test.external-messages
  (:require [clojure.test :refer :all]
            [bass4.test.core :refer [test-fixtures]]
            [clojure.core.async :refer [chan dropping-buffer <!!]]
            [bass4.email :as email]
            [clojure.tools.logging :as log]
            [bass4.external-messages :as external-messages]
            [clojure.string :as str]))

(use-fixtures
  :once
  test-fixtures)

(deftest async-send-out
  (let [res (with-out-str (binding [email/*mail-reroute*           :out
                                    external-messages/*debug-chan* (chan)]
                            (email/queue-email! "brjann@gmail.com" "XXX" "YYY")
                            (<!! external-messages/*debug-chan*)))]
    (is (str/includes? res "brjann@gmail.com"))
    (is (str/includes? res "XXX"))
    (is (str/includes? res "YYY"))))