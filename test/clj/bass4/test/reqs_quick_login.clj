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
            [clj-time.core :as t]
            [clojure.tools.logging :as log]
            [bass4.time :as b-time]
            [bass4.services.user :as user-service]
            [bass4.config :as config]))

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
    (let [user-id (user-service/create-user! 536103 {:Group "537404" :firstname "quick-login-test"})
          q-id    (str user-id "XXXX")]
      (user-service/update-user-properties! user-id {:QuickLoginPassword q-id :QuickLoginTimestamp (b-time/to-unix (t/now))})
      (-> *s*
          (visit (str "/q/" q-id))
          (has (status? 302))
          ;; Session created
          (follow-redirect)
          ;; Assessments checked
          (follow-redirect)
          (has (some-text? "Welcome"))
          (has (some-text? "top top welcome"))
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
          (has (some-text? "top top top thanks"))
          (has (some-text? "Thanks top"))))))

(deftest quick-login-expired
  (with-redefs [db/get-quick-login-settings (constantly {:allowed? true :expiration-days 11})]
    (let [user-id (user-service/create-user! 536103 {:Group "537404" :firstname "quick-login-test"})
          q-id    (str user-id "XXXX")]
      (user-service/update-user-properties! user-id {:QuickLoginPassword q-id :QuickLoginTimestamp (b-time/to-unix (t/now))})
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
    (let [user-id (user-service/create-user! 536103 {:Group "537404" :firstname "quick-login-test"})
          q-id    (str user-id "XXXX")]
      (user-service/update-user-properties! user-id {:QuickLoginPassword q-id :QuickLoginTimestamp (b-time/to-unix (t/now))})
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
    (let [user-id (user-service/create-user! 536103 {:Group "537404" :firstname "quick-login-test"})
          q-id    (str user-id "XXXX")]
      (user-service/update-user-properties! user-id {:QuickLoginPassword q-id :QuickLoginTimestamp (b-time/to-unix (t/now))})
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
  (with-redefs [db/get-quick-login-settings (constantly {:allowed? true :expiration-days 11})]
    (let [user-id             (user-service/create-user! 536103 {:Group "537404" :firstname "quick-login-escalation"})
          treatment-access-id (:objectid (db/create-bass-object! {:class-name    "cTreatmentAccess"
                                                                  :parent-id     user-id
                                                                  :property-name "TreatmentAccesses"}))
          q-id                (str user-id "XXXX")]
      (db/create-bass-link! {:linker-id     treatment-access-id
                             :linkee-id     551356
                             :link-property "Treatment"
                             :linker-class  "cTreatmentAccess"
                             :linkee-class  "cTreatment"})
      (user-service/update-user-properties! user-id {:username    user-id
                                             :password            user-id
                                             :QuickLoginPassword  q-id
                                             :QuickLoginTimestamp (b-time/to-unix (t/now))})
      (fix-time
        (-> *s*
            (visit (str "/q/" q-id))
            (has (status? 302))
            ;; Session created
            (follow-redirect)
            ;; Assessments checked
            (follow-redirect)
            (visit "/user/assessments" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"})
            (visit "/user/assessments" :request-method :post :params {:instrument-id 4743 :items "{}" :specifications "{}"})
            (visit "/user/assessments" :request-method :post :params {:instrument-id 4568 :items "{}" :specifications "{}"})
            (visit "/user/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
            (follow-redirect)
            (has (some-text? "top top top thanks"))
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