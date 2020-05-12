(ns bass4.test.reqs-privacy
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.assessment-utils :refer :all]
            [bass4.test.core :refer :all]
            [bass4.services.user :as user-service]
            [bass4.now :as now]
            [bass4.services.privacy :as privacy-service]
            [bass4.db.core :as db]
            [clj-time.core :as t]
            [bass4.utils :as utils]
            [bass4.routes.ext-login :as ext-login]
            [bass4.routes.quick-login :as quick-login]))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(use-fixtures
  :each
  random-date-tz-fixture)

(deftest privacy-consent-no-consent-then-consent-logout-complete-assessments
  (binding [privacy-service/user-must-consent? (constantly true)]
    (let [group-id (create-assessment-group! project-reg-allowed project-reg-allowed-ass-series [4431 4743 4568 286])
          user-id  (user-service/create-user! project-reg-allowed {:group group-id})]
      (user-service/update-user-properties! user-id {:username user-id :password user-id})
      (-> *s*
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (follow-redirect)
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "To continue to use"))
          (visit "/user/privacy/consent" :request-method :post :params {:i-consent "xxx"})
          (has (status? 400))
          (visit "/user/privacy/consent" :request-method :post :params {:i-consent "no-consent"})
          (follow-redirect)
          (visit "/user/tx")
          (has (status? 403))
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (has (status? 302))
          (follow-redirect)
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "To continue to use"))
          (visit "/user/assessments" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"})
          (has (status? 400))
          (visit "/user/privacy/consent" :request-method :post :params {:i-consent "i-consent"})
          (follow-redirect)
          (visit "/user/assessments" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"})
          (visit "/user/assessments" :request-method :post :params {:instrument-id 4743 :items "{}" :specifications "{}"})
          (visit "/logout")
          (visit "/user/tx")
          (has (status? 403))
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/user/assessments" :request-method :post :params {:instrument-id 4568 :items "{}" :specifications "{}"})
          (visit "/user/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
          (follow-redirect)
          (has (some-text? "Thanks"))))))

(deftest privacy-consent-consent-before-treatment
  (binding [privacy-service/user-must-consent? (constantly true)]
    (let [user-id (create-user-with-treatment! tx-autoaccess true)]
      (-> *s*
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "To continue to use"))
          (visit "/user/tx/messages")
          (has (status? 302))
          (visit "/user/privacy/consent")
          (has (status? 200))
          (visit "/user/privacy/consent" :request-method :post :params {:i-consent "i-consent"})
          (follow-redirect)
          (visit "/user/tx")
          (has (some-text? "Start page"))
          (visit "/user/tx/messages")
          (has (status? 200))
          (visit "/user/privacy/consent")
          (has (status? 302))))))

(deftest login-no-privacy-notice
  (binding [privacy-service/privacy-notice-exists? (constantly false)]
    (let [user-id (user-service/create-user! project-reg-allowed)]
      (user-service/update-user-properties! user-id {:username user-id :password user-id})
      (-> *s*
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (follow-redirect)
          (has (some-text? "User cannot login"))))))

(deftest quick-login-no-privacy-notice
  (binding [quick-login/db-quick-login-settings    (constantly {:allowed? true :expiration-days 11})
            privacy-service/privacy-notice-exists? (constantly false)]
    (let [user-id (user-service/create-user! project-reg-allowed)
          q-id    (str user-id "XXXX")]
      (user-service/update-user-properties! user-id {"QuickLoginPassword"  q-id
                                                     "QuickLoginTimestamp" (utils/to-unix (now/now))})
      (-> *s*
          (visit (str "/q/" q-id))
          (follow-redirect)
          (has (some-text? "User cannot login"))))))

(deftest request-ext-login-no-privacy-notice
  (binding [ext-login/db-ext-login-settings        (constantly {:allowed? true :ips "127.0.0.1"})
            privacy-service/privacy-notice-exists? (constantly false)]
    (let [user-id (user-service/create-user! project-reg-allowed)]
      (user-service/update-user-properties! user-id {"participantid" user-id})
      (-> *s*
          (visit (str "/ext-login/check-pending/" user-id))
          (has (some-text? "0 Privacy notice missing in DB"))))))

;; -----------------------
;;  NOTICE DISABLED TESTS
;; -----------------------

(deftest privacy-consent-notice-disabled
  (binding [privacy-service/user-must-consent?       (constantly true)
            privacy-service/privacy-notice-disabled? (constantly true)]
    (let [group-id (create-assessment-group! project-reg-allowed project-reg-allowed-ass-series)
          user-id  (user-service/create-user! project-reg-allowed {:group group-id})]
      (user-service/update-user-properties! user-id {:username user-id :password user-id})
      (-> *s*
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/user/privacy/consent")
          (has (status? 302))
          (visit "/user/privacy/consent" :request-method :post :params {:i-consent "no-consent"})
          (has (status? 400))))))

(deftest privacy-consent-consent-before-treatment-notice-disabled
  (binding [privacy-service/user-must-consent?       (constantly true)
            privacy-service/privacy-notice-disabled? (constantly true)]
    (let [user-id (create-user-with-treatment! tx-autoaccess true)]
      (-> *s*
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (follow-redirect)
          (visit "/user/tx")
          (has (some-text? "Start page"))
          (visit "/user/tx/messages")
          (has (status? 200))))))

(deftest login-no-privacy-notice-notice-disabled
  (binding [privacy-service/privacy-notice-exists?   (constantly false)
            privacy-service/privacy-notice-disabled? (constantly true)]
    (let [user-id (create-user-with-treatment! tx-autoaccess true)]
      (-> *s*
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "Welcome!"))))))

(deftest quick-login-no-privacy-notice-notice-disabled
  (binding [quick-login/db-quick-login-settings      (constantly {:allowed? true :expiration-days 11})
            privacy-service/privacy-notice-exists?   (constantly false)
            privacy-service/privacy-notice-disabled? (constantly true)]
    (let [group-id (create-assessment-group! project-reg-allowed project-reg-allowed-ass-series)
          user-id  (user-service/create-user! project-reg-allowed {:group group-id})
          q-id     (str user-id "XXXX")]
      (user-service/update-user-properties! user-id {"QuickLoginPassword"  q-id
                                                     "QuickLoginTimestamp" (utils/to-unix (now/now))})
      (-> *s*
          (visit (str "/q/" q-id))
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "Welcome"))))))

(deftest request-ext-login-no-privacy-notice-notice-disabled
  (binding [ext-login/db-ext-login-settings          (constantly {:allowed? true :ips "127.0.0.1"})
            privacy-service/privacy-notice-exists?   (constantly false)
            privacy-service/privacy-notice-disabled? (constantly true)]
    (let [group-id (create-assessment-group! project-reg-allowed project-reg-allowed-ass-series)
          user-id  (user-service/create-user! project-reg-allowed {:group group-id})]
      (user-service/update-user-properties! user-id {"participantid" user-id})
      (-> *s*
          (visit (str "/ext-login/check-pending/" user-id))
          (has (some-text? "do-login?uid="))))))

(deftest api-privacy-notice
  (binding [privacy-service/privacy-notice-exists? (constantly true)]
    (let [user-id (create-user-with-treatment! tx-autoaccess true)]
      (-> *s*
          (visit "/api/user/privacy-notice-html")
          (has (status? 403))
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (visit "/api/user/privacy-notice-html")
          (has (status? 200))))))