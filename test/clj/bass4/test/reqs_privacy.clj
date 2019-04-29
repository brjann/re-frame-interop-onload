(ns bass4.test.reqs-privacy
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.middleware.core :as mw]
            [bass4.test.core :refer [test-fixtures
                                     fn-not-text?
                                     log-return
                                     log-body
                                     log-headers
                                     log-status
                                     disable-attack-detector
                                     *s*]]
            [bass4.services.auth :as auth-service]
            [bass4.services.user :as user-service]
            [bass4.services.privacy :as privacy-service]
            [bass4.db.core :as db]
            [clj-time.core :as t]
            [clojure.tools.logging :as log]
            [bass4.time :as b-time]))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)


(deftest privacy-consent-no-consent-then-consent-logout-complete-assessments
  (with-redefs [privacy-service/user-must-consent? (constantly true)]
    (let [user-id (user-service/create-user! 536103 {:Group "537404" :firstname "privacy-test"})]
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
          (has (some-text? "Welcome top-priority"))
          (visit "/user/assessments" :request-method :post :params {:instrument-id 4568 :items "{}" :specifications "{}"})
          (visit "/user/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
          (follow-redirect)
          (has (some-text? "Thanks top"))))))

(deftest privacy-consent-consent-before-treatment
  (with-redefs [privacy-service/user-must-consent? (constantly true)]
    (let [user-id             (user-service/create-user! 543018 {:firstname "privacy-tx-test"})
          treatment-access-id (:objectid (db/create-bass-object! {:class-name    "cTreatmentAccess"
                                                                  :parent-id     user-id
                                                                  :property-name "TreatmentAccesses"}))]
      (db/create-bass-link! {:linker-id     treatment-access-id
                             :linkee-id     551356
                             :link-property "Treatment"
                             :linker-class  "cTreatmentAccess"
                             :linkee-class  "cTreatment"})
      (user-service/update-user-properties! user-id {:username user-id
                                                     :password user-id})
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
  (with-redefs [privacy-service/privacy-notice-exists? (constantly false)]
    (-> *s*
        (visit "/login" :request-method :post :params {:username "in-treatment" :password "IN-treatment88"})
        (follow-redirect)
        (has (some-text? "User cannot login")))))

(deftest quick-login-no-privacy-notice
  (with-redefs [db/get-quick-login-settings            (constantly {:allowed? true :expiration-days 11})
                privacy-service/privacy-notice-exists? (constantly false)]
    (let [user-id (user-service/create-user! 536103 {:Group "537404" :firstname "quick-login-test"})
          q-id    (str user-id "XXXX")]
      (user-service/update-user-properties! user-id {:QuickLoginPassword q-id :QuickLoginTimestamp (b-time/to-unix (t/now))})
      (-> *s*
          (visit (str "/q/" q-id))
          (follow-redirect)
          (has (some-text? "User cannot login"))))))

(deftest request-ext-login-no-privacy-notice
  (with-redefs [db/ext-login-settings                  (constantly {:allowed? true :ips "localhost"})
                privacy-service/privacy-notice-exists? (constantly false)]
    (let [user-id (user-service/create-user! 536103 {:Group "537404" :firstname "ext-login-test"})]
      (user-service/update-user-properties! user-id {:username user-id :password user-id :participantid user-id})
      (-> *s*
          (visit (str "/ext-login/check-pending/" user-id))
          (has (some-text? "0 Privacy notice missing in DB"))))))

;; -----------------------
;;  NOTICE DISABLED TESTS
;; -----------------------

(deftest privacy-consent-notice-disabled
  (with-redefs [privacy-service/user-must-consent?       (constantly true)
                privacy-service/privacy-notice-disabled? (constantly true)]
    (let [user-id (user-service/create-user! 536103 {:Group "537404" :firstname "privacy-test"})]
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
  (with-redefs [privacy-service/user-must-consent?       (constantly true)
                privacy-service/privacy-notice-disabled? (constantly true)]
    (let [user-id             (user-service/create-user! 543018 {:firstname "privacy-tx-test"})
          treatment-access-id (:objectid (db/create-bass-object! {:class-name    "cTreatmentAccess"
                                                                  :parent-id     user-id
                                                                  :property-name "TreatmentAccesses"}))]
      (db/create-bass-link! {:linker-id     treatment-access-id
                             :linkee-id     551356
                             :link-property "Treatment"
                             :linker-class  "cTreatmentAccess"
                             :linkee-class  "cTreatment"})
      (user-service/update-user-properties! user-id {:username user-id
                                                     :password user-id})
      (-> *s*
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (follow-redirect)
          (visit "/user/tx")
          (has (some-text? "Start page"))
          (visit "/user/tx/messages")
          (has (status? 200))))))

(deftest login-no-privacy-notice-notice-disabled
  (with-redefs [privacy-service/privacy-notice-exists?   (constantly false)
                privacy-service/privacy-notice-disabled? (constantly true)]
    (-> *s*
        (visit "/login" :request-method :post :params {:username "in-treatment" :password "IN-treatment88"})
        (follow-redirect)
        (follow-redirect)
        (has (some-text? "Your treatment started")))))

(deftest quick-login-no-privacy-notice-notice-disabled
  (with-redefs [db/get-quick-login-settings              (constantly {:allowed? true :expiration-days 11})
                privacy-service/privacy-notice-exists?   (constantly false)
                privacy-service/privacy-notice-disabled? (constantly true)]
    (let [user-id (user-service/create-user! 536103 {:Group "537404" :firstname "quick-login-test"})
          q-id    (str user-id "XXXX")]
      (user-service/update-user-properties! user-id {:QuickLoginPassword q-id :QuickLoginTimestamp (b-time/to-unix (t/now))})
      (-> *s*
          (visit (str "/q/" q-id))
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "Welcome"))))))

(deftest request-ext-login-no-privacy-notice-notice-disabled
  (with-redefs [db/ext-login-settings                    (constantly {:allowed? true :ips "localhost"})
                privacy-service/privacy-notice-exists?   (constantly false)
                privacy-service/privacy-notice-disabled? (constantly true)]
    (let [user-id (user-service/create-user! 536103 {:Group "537404" :firstname "ext-login-test"})]
      (user-service/update-user-properties! user-id {:username user-id :password user-id :participantid user-id})
      (-> *s*
          (visit (str "/ext-login/check-pending/" user-id))
          (has (some-text? "http://"))))))

(deftest api-privacy-notice
  (with-redefs [privacy-service/privacy-notice-exists? (constantly true)]
    (-> *s*
        (visit "/api/user/privacy-notice-html")
        (has (status? 403))
        (visit "/login" :request-method :post :params {:username "in-treatment" :password "IN-treatment88"})
        (visit "/api/user/privacy-notice-html")
        (has (status? 200)))))