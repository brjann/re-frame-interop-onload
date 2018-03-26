(ns bass4.test.reqs-messages
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures not-text? disable-attack-detector *s*]]
            [bass4.services.auth :as auth-service]
            [bass4.services.user :as user]
            [clj-time.core :as t]))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(deftest request-messages
  (with-redefs [t/now (constantly (t/date-time 2017 11 30 0 0 0))]
    (-> *s*
        (visit "/debug/set-session" :params {:identity 549821 :double-authed 1})
        (visit "/user/messages")
        (not-text? "New message"))
    (-> *s*
        (visit "/debug/set-session" :params {:identity 543021 :double-authed 1})
        (visit "/user/messages")
        (has (some-text? "New message")))))