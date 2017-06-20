(ns bass4.test.reqs-instrument
  (:require [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]))

(deftest request-double-testss
  (alter-var-root #'bass4.middleware/*skip-csrf* (constantly true))
  (-> (session (app))
      (visit "/instrument/1647")
      (has (status? 200))
      (visit "/instrument/1647" :request-method :post :params {})
      (has (status? 400))))

