(ns bass4.test.reqs-csrf
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.middleware.core :as mw]
            [bass4.test.core :refer :all]
            [bass4.services.user :as user-service]
            [bass4.db.core :as db]))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(deftest skip-csrf
  (is (= true mw/*skip-csrf*)))

(deftest test-csrf
  (binding [mw/*skip-csrf* false]
    (let [user-id (create-user-with-treatment! tx-autoaccess true)]
      (-> *s*
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (has (status? 302))
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/user/tx/messages")
          (has (status? 200))
          (visit "/user/tx/messages" :request-method :post :params {:text "{}"})
          (has (status? 403))
          (has (some-text? "Invalid anti-forgery token"))))))