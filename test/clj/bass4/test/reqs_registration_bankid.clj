(ns ^:eftest/synchronized
    bass4.test.reqs-registration-bankid
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer :all]
            [bass4.passwords :as passwords]
            [bass4.test.assessment-utils :refer :all]
            [bass4.test.bankid.mock-collect :as mock-collect]
            [bass4.test.bankid.mock-reqs-utils :as bankid-utils :refer [wait
                                                                        collect+wait
                                                                        user-opens-app!
                                                                        user-authenticates!
                                                                        user-cancels!]]
            [bass4.registration.services :as reg-service]
            [clojure.tools.logging :as log]))


(use-fixtures
  :once
  test-fixtures)

(use-fixtures
  :each
  random-date-tz-fixture
  bankid-utils/reqs-fixtures)

(deftest registration-flow-bankid
  (let [pnr       "191212121212"
        reg-group (create-assessment-group! project-reg-allowed project-reg-allowed-ass-series)]
      (binding [reg-service/registration-params (constantly {:allowed?                true
                                                             :fields                  #{:email
                                                                                        :sms-number
                                                                                        :pid-number
                                                                                        :first-name
                                                                                        :last-name}
                                                             :group                   reg-group
                                                             :allow-duplicate-email?  true
                                                             :allow-duplicate-sms?    true
                                                             :sms-countries           ["se" "gb" "dk" "no" "fi"]
                                                             :auto-username           :none
                                                             :bankid?                 true
                                                             :bankid-change-names?    false
                                                             :allow-duplicate-bankid? true})
                passwords/letters-digits        (constantly "METALLICA")]
      (-> *s*
          (visit "/registration/564610/")
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/registration/564610/form")
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "BankID"))
          (visit "/registration/564610/bankid" :request-method :post :params {:personnummer pnr})
          (follow-redirect)
          (has (some-text? "Contacting"))
          (user-authenticates! pnr)
          (collect+wait)
          (visit "/e-auth/bankid/collect" :request-method :post)
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "Who is collecting the data"))
          (visit "/registration/564610/privacy" :request-method :post :params {})
          (has (status? 400))
          (visit "/registration/564610/form")
          (has (status? 302))
          (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
          (visit "/registration/564610/form" :request-method :post :params {:email      "brjann@gmail.com"
                                                                            :sms-number "+46070717652"})
          (has (status? 302))
          (follow-redirect)
          (has (some-text? "Validate"))
          (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
          (has (status? 200))
          (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
          (has (status? 302))
          ;; Redirect to finish
          (follow-redirect)
          ;; Session created
          (follow-redirect)
          ;; Redirect to pending assessments
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/user/assessments")
          (has (some-text? "AAQ"))
          (visit "/user/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
          (follow-redirect)
          (has (some-text? "Thanks"))))))

(deftest registration-flow-bankid-no-name-change
  (let [pnr       "191212121212"
        reg-group (create-assessment-group! project-reg-allowed project-reg-allowed-ass-series)]
      (binding [reg-service/registration-params (constantly {:allowed?                true
                                                             :fields                  #{:email
                                                                                        :sms-number
                                                                                        :pid-number
                                                                                        :first-name
                                                                                        :last-name}
                                                             :group                   reg-group
                                                             :allow-duplicate-email?  true
                                                             :allow-duplicate-sms?    true
                                                             :sms-countries           ["se" "gb" "dk" "no" "fi"]
                                                             :auto-username           :none
                                                             :bankid?                 true
                                                             :bankid-change-names?    false
                                                             :allow-duplicate-bankid? true})
                passwords/letters-digits        (constantly "METALLICA")]
      (-> *s*
          (visit "/registration/564610/")
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/registration/564610/form")
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "BankID"))
          (visit "/registration/564610/bankid" :request-method :post :params {:personnummer pnr})
          (follow-redirect)
          (has (some-text? "Contacting"))
          (user-authenticates! pnr)
          (collect+wait)
          (visit "/e-auth/bankid/collect" :request-method :post)
          (follow-redirect)
          (visit "/registration/564610/form" :request-method :post :params {:email "brjann@gmail.com"})
          (has (status? 400))
          (visit "/registration/564610/form" :request-method :post :params {:email      "brjann@gmail.com"
                                                                            :sms-number "+46070717652"
                                                                            :first-name "Jason"
                                                                            :last-name  "Newsted"})
          (has (status? 400))
          (visit "/registration/564610/form" :request-method :post :params {:email      "brjann@gmail.com"
                                                                            :sms-number "+46070717652"
                                                                            :first-name "Johan"
                                                                            :last-name  "Bjureberg"})
          (has (status? 400))
          (visit "/registration/564610/form" :request-method :post :params {:email      "brjann@gmail.com"
                                                                            :sms-number "+46070717652"
                                                                            :first-name "Johan"
                                                                            :last-name  "Bjureberg"
                                                                            :pid-number "191212121212"})
          (has (status? 400))
          (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
          (visit "/registration/564610/form" :request-method :post :params {:email      "brjann@gmail.com"
                                                                            :sms-number "+46070717652"})
          (has (status? 302))
          (follow-redirect)
          (has (some-text? "Validate"))
          (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
          (has (status? 200))
          (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
          (has (status? 302))
          ;; Redirect to finish
          (follow-redirect)))))

(deftest registration-flow-bankid-name-change
  (let [pnr       "191212121212"
        reg-group (create-assessment-group! project-reg-allowed project-reg-allowed-ass-series)]
      (binding [reg-service/registration-params (constantly {:allowed?                true
                                                             :fields                  #{:email
                                                                                        :sms-number
                                                                                        :pid-number
                                                                                        :first-name
                                                                                        :last-name}
                                                             :group                   reg-group
                                                             :allow-duplicate-email?  true
                                                             :allow-duplicate-sms?    true
                                                             :sms-countries           ["se" "gb" "dk" "no" "fi"]
                                                             :auto-username           :none
                                                             :bankid?                 true
                                                             :bankid-change-names?    true
                                                             :allow-duplicate-bankid? true})
                passwords/letters-digits        (constantly "METALLICA")]
      (-> *s*
          (visit "/registration/564610/")
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/registration/564610/form")
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "BankID"))
          (visit "/registration/564610/bankid" :request-method :post :params {:personnummer pnr})
          (follow-redirect)
          (has (some-text? "Contacting"))
          (user-authenticates! pnr)
          (collect+wait)
          (visit "/e-auth/bankid/collect" :request-method :post)
          (follow-redirect)
          (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
          (visit "/registration/564610/form" :request-method :post :params {:email "brjann@gmail.com"})
          (has (status? 400))
          (visit "/registration/564610/form" :request-method :post :params {:email      "brjann@gmail.com"
                                                                            :sms-number "+46070717652"
                                                                            :first-name "Jason"
                                                                            :last-name  "Newsted"
                                                                            :pid-number "191212121212"})
          (has (status? 400))
          (visit "/registration/564610/form" :request-method :post :params {:email      "brjann@gmail.com"
                                                                            :sms-number "+46070717652"
                                                                            :first-name "Jason"
                                                                            :last-name  "Newsted"})
          (has (status? 302))
          (visit "/registration/564610/form" :request-method :post :params {:email      "brjann@gmail.com"
                                                                            :sms-number "+46070717652"
                                                                            :first-name "James"
                                                                            :last-name  "Hetfield"})
          (has (status? 302))
          (follow-redirect)
          (has (some-text? "Validate"))
          (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
          (has (status? 200))
          (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
          (has (status? 302))
          ;; Redirect to finish
          (follow-redirect)))))

(def sms-email-counter (atom 0))
(defn random-sms []
  (swap! sms-email-counter inc)
  (str "+46" (System/currentTimeMillis) @sms-email-counter))

(defn random-email []
  (swap! sms-email-counter inc)
  (str (System/currentTimeMillis) @sms-email-counter "@example.com"))

(def pnr-counter (atom 0))
(defn random-pnr []
  (swap! pnr-counter inc)
  (let [pnr (str (System/currentTimeMillis) @pnr-counter)]
    (subs pnr (- (count pnr) 12))))

(deftest registration-flow-bankid-resume2
  (let [pnr       (random-pnr)
        email     (random-email)
        sms       (random-sms)
        reg-group (create-assessment-group! project-reg-allowed project-reg-allowed-ass-series [286 4743])]
      (binding [reg-service/registration-params (constantly {:allowed?                true
                                                             :fields                  #{:email
                                                                                        :sms-number
                                                                                        :pid-number
                                                                                        :first-name
                                                                                        :last-name}
                                                             :group                   reg-group
                                                             :allow-resume?           true
                                                             :allow-duplicate-email?  false
                                                             :allow-duplicate-sms?    false
                                                             :sms-countries           ["se" "gb" "dk" "no" "fi"]
                                                             :auto-username           :none
                                                             :bankid?                 true
                                                             :bankid-change-names?    false
                                                             :allow-duplicate-bankid? false})
                passwords/letters-digits        (constantly "METALLICA")]
      (-> *s*
          (visit "/registration/564610/")
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/registration/564610/form")
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "BankID"))
          (visit "/registration/564610/bankid" :request-method :post :params {:personnummer pnr})
          (follow-redirect)
          (has (some-text? "Contacting"))
          (user-authenticates! pnr)
          (collect+wait)
          (visit "/e-auth/bankid/collect" :request-method :post)
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "Who is collecting the data"))
          (visit "/registration/564610/privacy" :request-method :post :params {})
          (has (status? 400))
          (visit "/registration/564610/form")
          (has (status? 302))
          (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
          (visit "/registration/564610/form" :request-method :post :params {:email      email
                                                                            :sms-number sms})
          (has (status? 302))
          (follow-redirect)
          (has (some-text? "Validate"))
          (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
          (has (status? 200))
          (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
          (has (status? 302))
          ;; Redirect to finish
          (follow-redirect)
          ;; Session created
          (follow-redirect)
          (has (some-text? "exact"))
          (visit "/user")
          ;; Redirect to pending assessments
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/user/assessments")
          (has (some-text? "AAQ"))
          (visit "/user/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
          (follow-redirect)
          (visit "/api/session/timeout-hard")
          (visit "/user/assessments")
          (has (status? 403)))
      (-> *s*
          (visit "/registration/564610/")
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/registration/564610/form")
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "BankID"))
          (visit "/registration/564610/bankid" :request-method :post :params {:personnummer pnr})
          (follow-redirect)
          (has (some-text? "Contacting"))
          (user-authenticates! pnr)
          (collect+wait)
          (visit "/e-auth/bankid/collect" :request-method :post)
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "Who is collecting the data"))
          (visit "/registration/564610/privacy" :request-method :post :params {})
          (has (status? 400))
          (visit "/registration/564610/form")
          (has (status? 302))
          (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
          (visit "/registration/564610/form" :request-method :post :params {:email      email
                                                                            :sms-number sms})
          (has (status? 302))
          (follow-redirect)
          (has (some-text? "Validate"))
          (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
          (has (status? 200))
          (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
          ;; Redirect to finish
          (follow-redirect)
          ;; Session created
          (follow-redirect)
          (has (some-text? "Continue registration"))
          (has (some-text? "exact"))
          (visit "/user")
          ;; Redirect to pending assessments
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/user/assessments")
          (has (some-text? "Agoraphobic"))
          (visit "/user/assessments" :request-method :post :params {:instrument-id 4743 :items "{}" :specifications "{}"})
          (follow-redirect)
          (has (some-text? "Thanks"))))))

(deftest registration-flow-bankid-resume
  (let [pnr1      (random-pnr)
        pnr2      (random-pnr)
        email     (random-email)
        sms       (random-sms)
        reg-group (create-assessment-group! project-reg-allowed project-reg-allowed-ass-series [286 4743])]
      (binding [reg-service/registration-params (constantly {:allowed?                true
                                                             :fields                  #{:email
                                                                                        :sms-number
                                                                                        :pid-number
                                                                                        :first-name
                                                                                        :last-name}
                                                             :group                   reg-group
                                                             :allow-resume?           true
                                                             :allow-duplicate-email?  false
                                                             :allow-duplicate-sms?    false
                                                             :sms-countries           ["se" "gb" "dk" "no" "fi"]
                                                             :auto-username           :none
                                                             :bankid?                 true
                                                             :bankid-change-names?    false
                                                             :allow-duplicate-bankid? false})
                passwords/letters-digits        (constantly "METALLICA")]
      (-> *s*
          (visit "/registration/564610/")
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/registration/564610/form")
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "BankID"))
          (visit "/registration/564610/bankid" :request-method :post :params {:personnummer pnr1})
          (follow-redirect)
          (has (some-text? "Contacting"))
          (user-authenticates! pnr1)
          (collect+wait)
          (visit "/e-auth/bankid/collect" :request-method :post)
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "Who is collecting the data"))
          (visit "/registration/564610/privacy" :request-method :post :params {})
          (has (status? 400))
          (visit "/registration/564610/form")
          (has (status? 302))
          (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
          (visit "/registration/564610/form" :request-method :post :params {:email      email
                                                                            :sms-number sms})
          (has (status? 302))
          (follow-redirect)
          (has (some-text? "Validate"))
          (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
          (has (status? 200))
          (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
          (has (status? 302))
          ;; Redirect to finish
          (follow-redirect)
          ;; Session created
          (follow-redirect)
          (has (some-text? "exact"))
          (visit "/user")
          ;; Redirect to pending assessments
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/user/assessments")
          (has (some-text? "AAQ"))
          (visit "/user/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
          (follow-redirect)
          (visit "/api/session/timeout-hard")
          (visit "/user/assessments")
          (has (status? 403)))
      (-> *s*
          (visit "/registration/564610/")
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/registration/564610/form")
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "BankID"))
          (visit "/registration/564610/bankid" :request-method :post :params {:personnummer pnr2})
          (follow-redirect)
          (has (some-text? "Contacting"))
          (user-authenticates! pnr2)
          (collect+wait)
          (visit "/e-auth/bankid/collect" :request-method :post)
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "Who is collecting the data"))
          (visit "/registration/564610/privacy" :request-method :post :params {})
          (has (status? 400))
          (visit "/registration/564610/form")
          (has (status? 302))
          (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
          (visit "/registration/564610/form" :request-method :post :params {:email      email
                                                                            :sms-number sms})
          (has (status? 302))
          (follow-redirect)
          (has (some-text? "Validate"))
          (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
          (has (status? 200))
          (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
          ;; Redirect to finish
          (follow-redirect)
          ;; Session created
          (has (some-text? "already exists"))))))