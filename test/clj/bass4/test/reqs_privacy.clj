(ns bass4.test.reqs-privacy
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.middleware.core :as mw]
            [bass4.test.core :refer [test-fixtures
                                     not-text?
                                     log-return
                                     log-body
                                     log-headers
                                     log-status
                                     disable-attack-detector
                                     *s*]]
            [bass4.services.auth :as auth-service]
            [bass4.services.user :as user]
            [bass4.services.privacy :as privacy-service]
            [bass4.db.core :as db]
            [clj-time.core :as t]
            [clojure.tools.logging :as log]
            [bass4.services.attack-detector :as a-d]))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)


(deftest privacy-consent-no-consent-then-consent
  (with-redefs [privacy-service/user-must-consent? (constantly true)]
    (let [user-id (user/create-user! 536103 {:Group "537404" :firstname "privacy-test"})]
      (user/update-user-properties! user-id {:username user-id :password user-id})
      (-> *s*
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (has (status? 302))
          (follow-redirect)
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "To continue to use"))
          (visit "/privacy/consent" :request-method :post :params {:i-consent "xxx"})
          (has (status? 400))
          (visit "/privacy/consent" :request-method :post :params {:i-consent "no-consent"})
          (follow-redirect)
          (visit "/user")
          (has (status? 403))
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (has (status? 302))
          (follow-redirect)
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "To continue to use"))
          (visit "/privacy/consent" :request-method :post :params {:i-consent "i-consent"})
          (log-headers)
          (follow-redirect)
          (log-headers)
          (follow-redirect)
          (visit "/assessments" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"})
          (visit "/assessments" :request-method :post :params {:instrument-id 4743 :items "{}" :specifications "{}"})
          (visit "/assessments" :request-method :post :params {:instrument-id 4568 :items "{}" :specifications "{}"})
          (visit "/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
          (has (status? 302))
          (follow-redirect)
          (has (some-text? "top top top thanks"))
          (has (some-text? "Thanks top"))))))
