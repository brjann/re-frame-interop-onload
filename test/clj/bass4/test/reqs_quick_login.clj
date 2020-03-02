(ns bass4.test.reqs-quick-login
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer :all]
            [bass4.test.assessment-utils :refer :all]
            [bass4.now :as now]
            [bass4.utils :as utils]
            [bass4.services.user :as user-service]
            [bass4.config :as config]
            [bass4.routes.quick-login :as quick-login]))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(use-fixtures
  :each
  random-date-tz-fixture
  (fn [f]
    (fix-time
      (f))))

(deftest quick-login-assessments
  (binding [quick-login/db-quick-login-settings (constantly {:allowed? true :expiration-days 11})]
    (let [group-id (create-assessment-group! project-reg-allowed project-reg-allowed-ass-series [4431 4743 286])
          user-id  (user-service/create-user! project-reg-allowed {:group group-id})
          q-id     (str user-id "XXXX")]
      (user-service/update-user-properties! user-id {"QuickLoginPassword"  q-id
                                                     "QuickLoginTimestamp" (utils/to-unix (now/now))})
      (-> *s*
          (visit (str "/q/" q-id))
          (has (status? 302))
          ;; Session created
          (follow-redirect)
          ;; Assessments checked
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/user/assessments")
          (has (some-text? "HAD"))
          (visit "/user/assessments" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"})
          (has (status? 302))
          (follow-redirect)
          (has (some-text? "Agoraphobic"))
          (visit "/user/assessments" :request-method :post :params {:instrument-id 4743 :items "{}" :specifications "{}"})
          ;; Posting answers to instrument not shown yet - advanced stuff!
          (visit "/user/assessments" :request-method :post :params {:instrument-id 4568 :items "{}" :specifications "{}"})
          (has (status? 302))
          (follow-redirect)
          (has (some-text? "AAQ"))
          (visit "/user/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
          (has (status? 302))
          (follow-redirect)
          (has (some-text? "Thanks"))))))

(deftest quick-login-expired
  (binding [quick-login/db-quick-login-settings (constantly {:allowed? true :expiration-days 11})]
    (let [user-id (user-service/create-user! project-reg-allowed)
          q-id    (str user-id "XXXX")]
      (user-service/update-user-properties! user-id {"QuickLoginPassword"  q-id
                                                     "QuickLoginTimestamp" (utils/to-unix (now/now))})
      (advance-time-d! 11)
      (-> *s*
          (visit (str "/q/" q-id))
          (has (status? 200))
          (has (some-text? "expired"))))))

(deftest quick-login-invalid
  (binding [quick-login/db-quick-login-settings (constantly {:allowed? true :expiration-days 11})]
    (-> *s*
        (visit "/q/123456789012345")
        (has (status? 200))
        (has (some-text? "Invalid")))))

(deftest quick-login-not-allowed
  (binding [quick-login/db-quick-login-settings (constantly {:allowed? false :expiration-days 11})]
    (let [user-id (user-service/create-user! project-reg-allowed)
          q-id    (str user-id "XXXX")]
      (user-service/update-user-properties! user-id {:QuickLoginPassword q-id :QuickLoginTimestamp (utils/to-unix (now/now))})
      (-> *s*
          (visit (str "/q/" q-id))
          (has (status? 200))
          (has (some-text? "not allowed"))))))

(deftest quick-login-too-long
  (binding [quick-login/db-quick-login-settings (constantly {:allowed? true :expiration-days 11})]
    (-> *s*
        (visit "/q/1234567890123456")
        (has (status? 200))
        (has (some-text? "too long")))))

(deftest quick-login-no-timeout
  (binding [quick-login/db-quick-login-settings (constantly {:allowed? true :expiration-days 11})]
    (let [group-id (create-assessment-group! project-reg-allowed project-reg-allowed-ass-series)
          user-id  (user-service/create-user! project-reg-allowed {:group group-id})
          q-id     (str user-id "XXXX")]
      (user-service/update-user-properties! user-id {"QuickLoginPassword" q-id "QuickLoginTimestamp" (utils/to-unix (now/now))})
      (-> *s*
          (visit (str "/q/" q-id))
          (has (status? 302))
          (follow-redirect)
          (visit "/user/assessments")
          (has (status? 200))
          (advance-time-s! (config/env :timeout-soft))
          (visit "/user/assessments")
          (has (status? 200))))))


(deftest quick-login-escalation-re-auth
  []
  (binding [quick-login/db-quick-login-settings (constantly {:allowed? true :expiration-days 11})]



    (let [group-id (create-assessment-group! project-reg-allowed project-reg-allowed-ass-series)
          user-id  (user-service/create-user! project-reg-allowed)
          q-id     (str user-id "XXXX")]
      (link-user-to-treatment! user-id tx-autoaccess {})
      (user-service/update-user-properties! user-id {:group                group-id
                                                     :username             user-id
                                                     :password             user-id
                                                     "QuickLoginPassword"  q-id
                                                     "QuickLoginTimestamp" (utils/to-unix (now/now))})
      (fix-time
        (-> *s*
            (visit (str "/q/" q-id))
            (has (status? 302))
            ;; Session created
            (follow-redirect)
            ;; Assessments checked
            (follow-redirect)
            (visit "/user/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
            (follow-redirect)
            (has (some-text? "Thanks"))
            (visit "/user/assessments")
            (follow-redirect)
            (visit "/user")
            (follow-redirect)
            (follow-redirect)
            (has (some-text? "Password needed"))
            (visit "/escalate" :request-method :post :params {:password "xxx"})
            (has (status? 422))
            (visit "/escalate" :request-method :post :params {:password user-id})
            (has (status? 302))
            (follow-redirect)
            (follow-redirect)
            (has (some-text? "Start page"))
            (advance-time-s! (config/env :timeout-soft))
            (visit "/user/")
            (follow-redirect)
            (has (some-text? "Authenticate again"))
            (visit "/re-auth" :request-method :post :params {:password "xxx"})
            (has (status? 422))
            (visit "/re-auth" :request-method :post :params {:password user-id})
            (has (status? 302))
            (visit "/user/tx")
            (has (some-text? "Start page")))))))