(ns bass4.test.reqs-assessment
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures]]
            [bass4.services.auth :as auth-service]
            [bass4.services.user :as user]
            [clj-time.core :as t]))

(deftest active-assessment
  (with-redefs [auth-service/double-auth-code (constantly "666777")
                t/now (constantly (t/date-time 2017 8 2 0 0 0))]
    (-> (session (app))
        (visit "/login" :request-method :post :params {:username "one-assessment" :password "one-assessment"})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "666777"))
        (visit "/double-auth" :request-method :post :params {:code "666777"})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "Welcome top-priority")))))
