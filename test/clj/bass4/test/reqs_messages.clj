(ns bass4.test.reqs-messages
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures]]
            [bass4.services.auth :as auth-service]
            [bass4.services.user :as user]))


(deftest request-messages
  (-> (session (app))
      (visit "/debug/set-session" :params {:identity 536975 :double-authed 1})
      (visit "/user/messages")
      (has (some-text? "A message"))
      (visit "/user/messages")))