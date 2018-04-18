(ns bass4.test.reqs-csrf
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.middleware.core :as mw]
            [bass4.test.core :refer [test-fixtures not-text? log-return disable-attack-detector *s*]]
            [bass4.services.user :as user]
            [bass4.db.core :as db]))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(deftest skip-csrf
  (is (= true mw/*skip-csrf*)))

(deftest test-csrf
  (binding [mw/*skip-csrf* false]
    (let [user-id (user/create-user! 536103 {:Group "537404" :firstname "csrf-test"})]
      (user/update-user-properties! user-id {:username user-id :password user-id})
      (-> *s*
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (has (status? 302))
          (follow-redirect)
          (has (some-text? "Welcome"))
          (has (some-text? "top top welcome"))
          (visit "/user/")
          (has (some-text? "HAD"))
          (visit "/user/" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"})
          (has (status? 403))
          (has (some-text? "Invalid anti-forgery token"))))))
