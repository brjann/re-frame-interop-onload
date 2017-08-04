(ns bass4.test.reqs-instrument
  (:require [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures debug-headers-text?]]
            [clojure.string :as string]))


(deftest request-post-answers
  (alter-var-root #'bass4.middleware.core/*skip-csrf* (constantly true))
  (-> (session (app))
      (visit "/instrument/1647")
      (has (status? 200))
      (visit "/instrument/1647" :request-method :post :params {})
      (has (status? 400))
      (debug-headers-text? "MAIL" "schema")
      (visit "/instrument/1647" :request-method :post :params {:items "x" :specifications "y"})
      (debug-headers-text? "MAIL" "JSON")
      (has (status? 400))
      (visit "/instrument/1647" :request-method :post :params {:items "{}" :specifications "{}"})
      (has (status? 302))))


(deftest request-wrong-instrument
  (-> (session (app))
      (visit "/instrument/hell-is-here")
      (has (status? 404))))