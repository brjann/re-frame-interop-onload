(ns bass4.test.bankid-api
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [clojure.core.async :refer [<!! timeout]]
            [bass4.test.core :refer [test-fixtures
                                     debug-headers-text?
                                     debug-headers-not-text?
                                     log-return
                                     *s*
                                     advance-time-s!
                                     fix-time
                                     test-now]]
            [bass4.captcha :as captcha]
            [bass4.config :refer [env]]
            [bass4.db.core :as db]
            [bass4.services.auth :as auth-service]
            [bass4.middleware.core :as mw]
            [bass4.services.registration :as reg-service]
            [bass4.services.attack-detector :as a-d]
    #_[bass4.services.bankid-mock-api :as bankid-mock]
            [bass4.test.bankid.mock-collect :as mock-collect]
            [clojure.tools.logging :as log]
            [bass4.services.bankid :as bankid]
            [clj-time.core :as t]))


(use-fixtures
  :once
  test-fixtures)


(use-fixtures
  :each
  (fn [f]
    (swap! test-now (constantly (t/now)))
    (binding [bankid/bankid-now (fn [] @test-now)]
      ((mock-collect/wrap-mock :immediate) f))))


(deftest loop-timeout
  (let [uid (bankid/launch-user-bankid "191212121212" "127.0.0.1" :prod)]
    (<!! (timeout 10))
    (is (true? (bankid/session-active? (bankid/get-session-info uid))))
    (advance-time-s! 299)
    (<!! (timeout 10))
    (is (true? (bankid/session-active? (bankid/get-session-info uid))))
    (advance-time-s! 300)
    (<!! (timeout 10))
    (let [info (bankid/get-session-info uid)]
      (is (= {:status     :error
              :error-code :loop-timeout}
             (select-keys info [:status :error-code]))))))