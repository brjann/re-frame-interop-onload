(ns bass4.test.reqs-instrument
  (:require [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]))

(deftest request-post-answers
  (alter-var-root #'bass4.middleware/*skip-csrf* (constantly true))
  (-> (session (app))
      (visit "/instrument/1647")
      (has (status? 200))
      (visit "/instrument/1647" :request-method :post :params {})
      (has (status? 400))
      (visit "/instrument/1647" :request-method :post :params {:items "x" :specifications "y"})
      (has (status? 400))
      (visit "/instrument/1647" :request-method :post :params {:items "{}" :specifications "{}"})
      (has (status? 302))))

(deftest request-wrong-instrument
  (-> (session (app))
      (visit "/instrument/hell-is-here")
      (has (status? 404))))