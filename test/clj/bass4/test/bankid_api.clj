(ns bass4.test.bankid-api
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [clojure.core.async :refer [<!! >!! timeout chan]]
            [bass4.test.core :refer [test-fixtures
                                     debug-headers-text?
                                     debug-headers-not-text?
                                     log-return
                                     *s*
                                     advance-time-s!
                                     fix-time
                                     test-now]]
            [bass4.config :refer [env]]
            [bass4.test.bankid.mock-collect :as mock-collect]
            [clojure.tools.logging :as log]
            [bass4.bankid.services :as bankid-service]
            [clj-time.core :as t]))


(use-fixtures
  :once
  test-fixtures)


(use-fixtures
  :each
  (fn [f]
    (swap! test-now (constantly (t/now)))
    (binding [bankid-service/bankid-now (fn [] @test-now)]
      ((mock-collect/wrap-mock :immediate) f))))


(deftest loop-timeout
  (binding [bankid-service/collect-waiter nil]
    (let [res-chan  (chan)
          wait-chan (chan)]
      (bankid-service/launch-bankid "191212121212" "127.0.0.1" :prod (fn [] wait-chan) res-chan)
      (is (true? (bankid-service/session-active? (<!! res-chan))))
      (>!! wait-chan true)
      (advance-time-s! 299)
      (is (true? (bankid-service/session-active? (<!! res-chan))))
      (advance-time-s! 300)
      (>!! wait-chan true)
      (let [info (<!! res-chan)]
        (is (false? (bankid-service/session-active? info)))
        (is (= {:status     :error
                :error-code :loop-timeout}
               (select-keys info [:status :error-code])))))))

(comment
  "Timeout guard. Check that collect loop completes after 5 secs"
  (binding [bankid-service/collect-waiter nil]
    (let [res-chan  (chan)
          wait-chan (chan)]
      (bankid-service/launch-bankid "191212121212" "127.0.0.1" :prod (fn [] wait-chan) res-chan)
      (is (true? (bankid-service/session-active? (<!! res-chan))))))
  "Timeout guard. Check that collect loop completes after 5 secs"
  (binding [bankid/collect-waiter nil]
    (let [res-chan  (chan)
          wait-chan (chan)]
      (bankid-service/launch-bankid "191212121212" "127.0.0.1" :prod (fn [] wait-chan) res-chan)
      (is (true? (bankid-service/session-active? (<!! res-chan))))
      (>!! wait-chan true))))