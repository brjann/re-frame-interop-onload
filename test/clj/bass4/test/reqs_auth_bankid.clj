(ns bass4.test.reqs-auth-bankid
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer :all]
            [bass4.services.auth :as auth-service]
            [bass4.test.assessment-utils :refer :all]
            [bass4.test.bankid.mock-reqs-utils :as bankid-utils :refer :all]
            [bass4.now :as now]
            [clj-time.core :as t]
            [clojure.string :as str]))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(use-fixtures
  :each
  bankid-utils/reqs-fixtures)

(deftest bankid-login-settings
  (binding [auth-service/db-bankid-login? (constantly false)]
    (-> *s*
        (visit "/login")
        (has (not-text? "BankID"))
        (visit "/bankid-login")
        (has (status? 403))))
  (binding [auth-service/db-bankid-login? (constantly true)]
    (-> *s*
        (visit "/login")
        (has (some-text? "BankID"))
        (visit "/bankid-login")
        (has (status? 200)))))


(defonce pnr-counter (atom 0))

(defn random-pnr []
  (let [u   (swap! pnr-counter inc)
        pnr (str u (str/reverse (str (System/currentTimeMillis))))
        res (subs pnr 0 12)]
    res))

(deftest bankid-login-assessment+tx
  (binding [auth-service/db-bankid-login? (constantly true)
            *tz*                          (t/time-zone-for-id "Europe/Stockholm")]
    (let [assessment (create-assessment! project-double-auth-assessment-series
                                         {"Scope" 0})
          pnr        (random-pnr)
          user-id    (create-user-with-password! {"personnummer" pnr})]
      (link-instrument! assessment 286)                     ; AAQ
      (create-participant-administration! user-id assessment 1 {:date (midnight (now/now))})
      (link-user-to-treatment! user-id tx-autoaccess {})
      (-> *s*
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          ;; No valid double auth
          (has (status? 422))
          (visit "/bankid-login" :request-method :post :params {:personnummer pnr})
          (follow-redirect)
          (has (some-text? "Contacting"))
          (user-authenticates! pnr)
          (collect+wait)
          (visit "/e-auth/bankid/collect" :request-method :post)
          (follow-redirect)
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "AAQ"))
          (visit "/user/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
          (follow-redirect)
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "Welcome"))))))

(deftest bankid-login-no-user
  (binding [auth-service/db-bankid-login? (constantly true)]
    (let [pnr (random-pnr)]
      (-> *s*
          (visit "/bankid-login" :request-method :post :params {:personnummer pnr})
          (follow-redirect)
          (has (some-text? "Contacting"))
          (user-authenticates! pnr)
          (collect+wait)
          (visit "/e-auth/bankid/collect" :request-method :post)
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "Användare saknas"))))))

(deftest bankid-login-two-users
  (binding [auth-service/db-bankid-login? (constantly true)]
    (let [pnr (random-pnr)]
      (create-user-with-password! {"personnummer" pnr})
      (create-user-with-password! {"personnummer" pnr})
      (-> *s*
          (visit "/bankid-login" :request-method :post :params {:personnummer pnr})
          (follow-redirect)
          (has (some-text? "Contacting"))
          (user-authenticates! pnr)
          (collect+wait)
          (visit "/e-auth/bankid/collect" :request-method :post)
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "Användare saknas"))))))
