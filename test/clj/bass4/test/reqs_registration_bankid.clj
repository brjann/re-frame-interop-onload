(ns bass4.test.reqs-registration-bankid
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures debug-headers-text? log-return disable-attack-detector *s* pass-by ->! log-body]]
            [bass4.services.auth :as auth-service]
            [bass4.services.user :as user]
            [bass4.services.bankid :as bankid]
            [bass4.services.bankid-mock :as bankid-mock]
            [bass4.middleware.debug :as debug]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [bass4.services.attack-detector :as a-d]
            [clojure.data.json :as json]
            [bass4.i18n :as i18n]
            [bass4.services.registration :as reg-service]))


(use-fixtures
  :once
  test-fixtures)

(use-fixtures
  :each
  (bankid-mock/wrap-mock :manual))

(defn user-opens-app!
  [x pnr]
  (bankid-mock/user-opens-app! pnr)
  x)

(defn user-cancels!
  [x pnr]
  (bankid-mock/user-cancels! pnr)
  x)

(defn user-authenticates!
  [x pnr]
  (bankid-mock/user-authenticates! pnr)
  x)

(deftest registration-flow-bankid
  (let [pnr "191212121212"]
    (with-redefs [reg-service/registration-params (constantly {:allowed?               true
                                                               :fields                 #{:email
                                                                                         :sms-number
                                                                                         :pid-number
                                                                                         :first-name
                                                                                         :last-name}
                                                               :group                  564616
                                                               :allow-duplicate-email? true
                                                               :allow-duplicate-sms?   true
                                                               :sms-countries          ["se" "gb" "dk" "no" "fi"]
                                                               :auto-username          :none
                                                               :bankid?                true
                                                               :bankid-change-names?   false})
                  auth-service/letters-digits     (constantly "METALLICA")]
      (-> *s*
          (visit "/registration/564610/")
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/registration/564610/form")
          (follow-redirect)
          (has (some-text? "BankID"))
          (visit "/registration/564610/bankid" :request-method :post :params {:personnummer pnr})
          (follow-redirect)
          (has (some-text? "Contacting"))
          (user-authenticates! pnr)
          (visit "/e-auth/bankid/collect" :request-method :post)
          (follow-redirect)
          (visit "/registration/564610/form" :request-method :post :params {:email      "brjann@gmail.com"
                                                                            :sms-number "+46070717652"})
          (has (status? 302))
          (debug-headers-text? "MAIL" "SMS" "METALLICA")
          (follow-redirect)
          (has (some-text? "Validate"))
          (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
          (has (status? 200))
          (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
          (has (status? 302))
          ;; Redirect to finish
          (follow-redirect)
          ;; Redirect to pending assessments
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/user")
          (has (some-text? "AAQ"))
          (visit "/user/" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
          (follow-redirect)
          (has (some-text? "Thanks"))
          (visit "/user")
          ;; Assessments completed
          (follow-redirect)
          ;; Redirect to finish screen
          (follow-redirect)
          (has (some-text? "Login"))))))

(deftest registration-flow-bankid-no-name-change
  (let [pnr "191212121212"]
    (with-redefs [reg-service/registration-params (constantly {:allowed?               true
                                                               :fields                 #{:email
                                                                                         :sms-number
                                                                                         :pid-number
                                                                                         :first-name
                                                                                         :last-name}
                                                               :group                  564616
                                                               :allow-duplicate-email? true
                                                               :allow-duplicate-sms?   true
                                                               :sms-countries          ["se" "gb" "dk" "no" "fi"]
                                                               :auto-username          :none
                                                               :bankid?                true
                                                               :bankid-change-names?   false})
                  auth-service/letters-digits     (constantly "METALLICA")]
      (-> *s*
          (visit "/registration/564610/")
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/registration/564610/form")
          (follow-redirect)
          (has (some-text? "BankID"))
          (visit "/registration/564610/bankid" :request-method :post :params {:personnummer pnr})
          (follow-redirect)
          (has (some-text? "Contacting"))
          (user-authenticates! pnr)
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
          (visit "/registration/564610/form" :request-method :post :params {:email      "brjann@gmail.com"
                                                                            :sms-number "+46070717652"})
          (has (status? 302))
          (debug-headers-text? "MAIL" "SMS" "METALLICA")
          (follow-redirect)
          (has (some-text? "Validate"))
          (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
          (has (status? 200))
          (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
          (has (status? 302))
          ;; Redirect to finish
          (follow-redirect)))))

(deftest registration-flow-bankid-name-change
  (let [pnr "191212121212"]
    (with-redefs [reg-service/registration-params (constantly {:allowed?               true
                                                               :fields                 #{:email
                                                                                         :sms-number
                                                                                         :pid-number
                                                                                         :first-name
                                                                                         :last-name}
                                                               :group                  564616
                                                               :allow-duplicate-email? true
                                                               :allow-duplicate-sms?   true
                                                               :sms-countries          ["se" "gb" "dk" "no" "fi"]
                                                               :auto-username          :none
                                                               :bankid?                true
                                                               :bankid-change-names?   true})
                  auth-service/letters-digits     (constantly "METALLICA")]
      (-> *s*
          (visit "/registration/564610/")
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/registration/564610/form")
          (follow-redirect)
          (has (some-text? "BankID"))
          (visit "/registration/564610/bankid" :request-method :post :params {:personnummer pnr})
          (follow-redirect)
          (has (some-text? "Contacting"))
          (user-authenticates! pnr)
          (visit "/e-auth/bankid/collect" :request-method :post)
          (follow-redirect)
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
          (debug-headers-text? "MAIL" "SMS" "METALLICA")
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
