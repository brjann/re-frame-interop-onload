(ns bass4.test.reqs-quick-login
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures
                                     not-text?
                                     log-return
                                     log-headers
                                     log-body
                                     disable-attack-detector
                                     *s*
                                     fix-time
                                     advance-time-d!
                                     modify-session
                                     advance-time-s!]]
            [bass4.db.core :as db]
            [bass4.services.user :as user]
            [bass4.responses.auth :refer [re-auth-timeout]]
            [clj-time.core :as t]
            [clojure.tools.logging :as log]
            [bass4.time :as b-time]))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(use-fixtures
  :each
  (fn [f]
    (fix-time
      (f))))

(deftest quick-login-assessments
  (with-redefs [db/get-quick-login-settings (constantly {:allowed? true :expiration-days 11})]
    (let [user-id (user/create-user! 536103 {:Group "537404" :firstname "quick-login-test"})
          q-id    (str user-id "XXXX")]
      (user/update-user-properties! user-id {:QuickLoginPassword q-id :QuickLoginTimestamp (b-time/to-unix (t/now))})
      (-> *s*
          (visit (str "/q/" q-id))
          (has (status? 302))
          ;; Session created
          (follow-redirect)
          ;; Assessments checked
          (follow-redirect)
          (has (some-text? "Welcome"))
          (has (some-text? "top top welcome"))
          (visit "/assessments")
          (has (some-text? "HAD"))
          (visit "/assessments" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"})
          (has (status? 302))
          (follow-redirect)
          (has (some-text? "Agoraphobic"))
          (visit "/assessments" :request-method :post :params {:instrument-id 4743 :items "{}" :specifications "{}"})
          ;; Posting answers to instrument not shown yet - advanced stuff!
          (visit "/assessments" :request-method :post :params {:instrument-id 4568 :items "{}" :specifications "{}"})
          (has (status? 302))
          (follow-redirect)
          (has (some-text? "AAQ"))
          (visit "/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
          (has (status? 302))
          (follow-redirect)
          (has (some-text? "top top top thanks"))
          (has (some-text? "Thanks top"))))))

(deftest quick-login-expired
  (with-redefs [db/get-quick-login-settings (constantly {:allowed? true :expiration-days 11})]
    (let [user-id (user/create-user! 536103 {:Group "537404" :firstname "quick-login-test"})
          q-id    (str user-id "XXXX")]
      (user/update-user-properties! user-id {:QuickLoginPassword q-id :QuickLoginTimestamp (b-time/to-unix (t/now))})
      (advance-time-d! 11)
      (-> *s*
          (visit (str "/q/" q-id))
          (has (status? 200))
          (has (some-text? "expired"))))))

(deftest quick-login-invalid
  (with-redefs [db/get-quick-login-settings (constantly {:allowed? true :expiration-days 11})]
    (-> *s*
        (visit "/q/123456789012345")
        (has (status? 200))
        (has (some-text? "Invalid")))))

(deftest quick-login-not-allowed
  (with-redefs [db/get-quick-login-settings (constantly {:allowed? false :expiration-days 11})]
    (let [user-id (user/create-user! 536103 {:Group "537404" :firstname "quick-login-test"})
          q-id    (str user-id "XXXX")]
      (user/update-user-properties! user-id {:QuickLoginPassword q-id :QuickLoginTimestamp (b-time/to-unix (t/now))})
      (-> *s*
          (visit (str "/q/" q-id))
          (has (status? 200))
          (has (some-text? "not allowed"))))))

(deftest quick-login-too-long
  (with-redefs [db/get-quick-login-settings (constantly {:allowed? true :expiration-days 11})]
    (-> *s*
        (visit "/q/1234567890123456")
        (has (status? 200))
        (has (some-text? "too long")))))

(deftest quick-login-no-timeout
  (with-redefs [db/get-quick-login-settings (constantly {:allowed? true :expiration-days 11})]
    (let [user-id (user/create-user! 536103 {:Group "537404" :firstname "quick-login-test"})
          q-id    (str user-id "XXXX")]
      (user/update-user-properties! user-id {:QuickLoginPassword q-id :QuickLoginTimestamp (b-time/to-unix (t/now))})
      (-> *s*
          (visit (str "/q/" q-id))
          (has (status? 302))
          (follow-redirect)
          (visit "/assessments")
          (has (status? 200))
          (advance-time-s! (re-auth-timeout))
          (visit "/assessments")
          (has (status? 200))))))


(deftest quick-login-escalation-re-auth
  []
  (with-redefs [db/get-quick-login-settings (constantly {:allowed? true :expiration-days 11})]
    (let [user-id             (user/create-user! 536103 {:Group "537404" :firstname "quick-login-escalation"})
          treatment-access-id (:objectid (db/create-bass-object! {:class-name    "cTreatmentAccess"
                                                                  :parent-id     user-id
                                                                  :property-name "TreatmentAccesses"}))
          q-id                (str user-id "XXXX")]
      (db/create-bass-link! {:linker-id     treatment-access-id
                             :linkee-id     551356
                             :link-property "Treatment"
                             :linker-class  "cTreatmentAccess"
                             :linkee-class  "cTreatment"})
      (user/update-user-properties! user-id {:username            user-id
                                             :password            user-id
                                             :QuickLoginPassword  q-id
                                             :QuickLoginTimestamp (b-time/to-unix (t/now))})
      (-> *s*
          (visit (str "/q/" q-id))
          (has (status? 302))
          ;; Session created
          (follow-redirect)
          ;; Assessments checked
          (follow-redirect)
          (visit "/assessments" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"})
          (visit "/assessments" :request-method :post :params {:instrument-id 4743 :items "{}" :specifications "{}"})
          (visit "/assessments" :request-method :post :params {:instrument-id 4568 :items "{}" :specifications "{}"})
          (visit "/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
          (follow-redirect)
          (has (some-text? "top top top thanks"))
          (visit "/assessments")
          (follow-redirect)
          (visit "/user")
          (follow-redirect)
          (has (some-text? "Password needed"))
          (visit "/escalate" :request-method :post :params {:password "xxx"})
          (has (status? 422))
          (visit "/escalate" :request-method :post :params {:password user-id})
          (has (status? 302))
          (follow-redirect)
          (has (some-text? "Start page"))
          (modify-session {:last-request-time (t/date-time 1985 10 26 1 20 0 0)})
          (visit "/user/")
          (follow-redirect)
          (has (some-text? "Authenticate again"))
          (visit "/re-auth" :request-method :post :params {:password "xxx"})
          (has (status? 422))
          (visit "/re-auth" :request-method :post :params {:password user-id})
          (has (status? 302))
          (visit "/user/")
          (has (some-text? "Start page"))))))