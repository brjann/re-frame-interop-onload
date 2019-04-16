(ns bass4.test.reqs-registration
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [clojure.core.async :refer [chan dropping-buffer]]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures
                                     debug-headers-text?
                                     log-return
                                     log-headers
                                     log-body
                                     log-status
                                     disable-attack-detector
                                     *s*
                                     pass-by
                                     messages-are?
                                     fix-time
                                     modify-session
                                     poll-message-chan]]
            [bass4.captcha :as captcha]
            [bass4.config :refer [env]]
            [bass4.db.core :as db]
            [bass4.middleware.debug :as debug]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [clojure.string :as string]
            [bass4.registration.services :as reg-service]
            [bass4.external-messages :refer [*debug-chan*]]
            [bass4.passwords :as passwords]
            [bass4.services.attack-detector :as a-d]
            [net.cgrand.enlive-html :as enlive]
            [bass4.services.privacy :as privacy-service]))


(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(use-fixtures
  :each
  (fn [f]
    (binding [*debug-chan* (chan 2)]
      (f))))

(deftest registration-not-allowed
  (-> *s*
      (visit "/registration/564613/info")
      (has (some-text? "Registration not allowed"))
      (visit "/registration/metallica/info")
      (has (status? 404))
      (visit "/registration/666/info")
      (has (status? 404))))

(deftest registration-flow
  (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                reg-service/registration-params (constantly {:allowed?               true
                                                             :fields                 #{:email :sms-number}
                                                             :group                  564616
                                                             :allow-duplicate-email? true
                                                             :allow-duplicate-sms?   true
                                                             :sms-countries          ["se" "gb" "dk" "no" "fi"]
                                                             :auto-username          :none
                                                             :study-consent?         true})
                passwords/letters-digits        (constantly "METALLICA")]
    (-> *s*
        (visit "/registration/564610")
        (follow-redirect)
        (has (some-text? "Welcome"))
        (visit "/registration/564610/captcha")
        ;; Captcha session is created
        (follow-redirect)
        (has (some-text? "code below"))
        (visit "/registration/564610/captcha" :request-method :post :params {:captcha "234234"})
        (has (status? 422))
        (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
        (has (status? 302))
        (visit "/registration/564610/captcha")
        (has (status? 302))
        (follow-redirect)
        (follow-redirect)
        (has (some-text? "Who is collecting the data"))
        (visit "/registration/564610/privacy" :request-method :post :params {})
        (pass-by (messages-are?
                   [[:email "API"]]
                   (poll-message-chan *debug-chan*)))
        (has (status? 400))
        (visit "/registration/564610/form")
        (has (status? 302))
        (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
        (has (status? 302))
        (follow-redirect)
        (follow-redirect)
        (has (some-text? "I want to be in the study"))
        (visit "/registration/564610/form")
        (follow-redirect)
        (has (some-text? "I want to be in the study"))
        (visit "/registration/564610/study-consent" :request-method :post :params {:i-consent "i-consent"})
        (follow-redirect)
        (has (some-text? "Enter your"))
        (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
        (has (status? 400))
        (visit "/registration/564610/form" :request-method :post :params {:captcha "234234"})
        (has (status? 400))
        (visit "/registration/564610/form" :request-method :post :params {:email "brjann@gmail.com"})
        (has (status? 400))
        (visit "/registration/564610/form" :request-method :post :params {:email "brjann@gmail.com" :sms-number "+11343454354"})
        (has (status? 422))
        (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "3434"})
        (has (status? 400))
        (visit "/registration/564610/validate-email" :request-method :post :params {:something-happened "3434"})
        (has (status? 400))
        (visit "/registration/564610/form" :request-method :post :params {:email "brjann@gmail.com" :sms-number "+46070717652"})
        (has (status? 302))
        (pass-by (messages-are?
                   [[:email "METALLICA"]
                    [:sms "METALLICA"]]
                   (poll-message-chan *debug-chan* 2)))
        (follow-redirect)
        (has (some-text? "Validate"))
        (visit "/registration/564610/form")
        (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "3434" :code-sms "345345"})
        (has (status? 422))
        (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA" :code-sms "345345"})
        (has (status? 200))
        (visit "/registration/564610/validate-sms" :request-method :post :params {:code-email "METALLICA" :code-sms "METALLICA"})
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
        (has (some-text? "Thanks"))
        (visit "/user/assessments")
        ;; Assessments completed
        (follow-redirect)
        ;; Redirect to finish screen
        (follow-redirect)
        (has (some-text? "Login")))))

(deftest registration-no-study-consent
  (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                reg-service/registration-params (constantly {:allowed?               true
                                                             :fields                 #{:email :sms-number}
                                                             :group                  564616
                                                             :allow-duplicate-email? true
                                                             :allow-duplicate-sms?   true
                                                             :sms-countries          ["se" "gb" "dk" "no" "fi"]
                                                             :auto-username          :none
                                                             :study-consent?         false})
                passwords/letters-digits        (constantly "METALLICA")]
    (-> *s*
        (visit "/registration/564610/captcha")
        ;; Captcha session is created
        (follow-redirect)
        (has (some-text? "code below"))
        (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
        (follow-redirect)
        (has (some-text? "Who is collecting the data"))
        (visit "/registration/564610/privacy" :request-method :post :params {})
        (pass-by (messages-are?
                   [[:email "API"]]
                   (poll-message-chan *debug-chan*)))
        (has (status? 400))
        (visit "/registration/564610/form")
        (has (status? 302))
        (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "Enter your"))
        (visit "/registration/564610/study-consent")
        (follow-redirect)
        (has (some-text? "Enter your"))
        (visit "/registration/564610/study-consent" :request-method :post :params {:i-consent "i-consent"})
        (has (status? 400)))))

(deftest registration-change-sms
  (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                reg-service/registration-params (constantly {:allowed?               true
                                                             :fields                 #{:email :sms-number}
                                                             :group                  564616
                                                             :allow-duplicate-email? true
                                                             :allow-duplicate-sms?   true
                                                             :sms-countries          ["se" "gb" "dk" "no" "fi"]
                                                             :auto-username          :none})
                passwords/letters-digits        (let [pos (atom 0)]
                                                  (fn [& _]
                                                    (let [code (int (Math/floor (/ @pos 2)))]
                                                      (swap! pos inc)
                                                      (str "code-" code))))]
    (-> *s*
        (visit "/registration/564610/captcha")
        ;; Captcha session is created
        (follow-redirect)
        (has (some-text? "code below"))
        (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
        (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
        (visit "/registration/564610/form" :request-method :post :params {:captcha "234234"})
        (has (status? 400))
        (visit "/registration/564610/form" :request-method :post :params {:email "brjann@gmail.com"})
        (has (status? 400))
        (visit "/registration/564610/form" :request-method :post :params {:email "brjann@gmail.com" :sms-number "+11343454354"})
        (has (status? 422))
        (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "3434"})
        (has (status? 400))
        (visit "/registration/564610/validate-email" :request-method :post :params {:something-happened "3434"})
        (has (status? 400))
        (visit "/registration/564610/form" :request-method :post :params {:email "brjann@gmail.com" :sms-number "+46070717652"})
        (has (status? 302))
        (pass-by (messages-are?
                   [[:email "code-0"]
                    [:sms "code-0"]]
                   (poll-message-chan *debug-chan* 2)))
        (follow-redirect)
        (has (some-text? "Validate"))
        (visit "/registration/564610/form")
        (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "3434"})
        (has (status? 422))
        (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "3434"})
        (has (status? 422))
        (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "code-0" :code-sms "345345"})
        (has (status? 200))
        (visit "/registration/564610/form")
        (has (status? 200))
        (visit "/registration/564610/form" :request-method :post :params {:email      "brjann@gmail.com"
                                                                          :sms-number "+46070717652"})
        (has (status? 400))
        (visit "/registration/564610/form" :request-method :post :params {:sms-number "+460707000000"})
        (has (status? 302))
        (pass-by (messages-are?
                   [[:sms "code-1"]]
                   (poll-message-chan *debug-chan* 1)))
        (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "code-0"})
        (has (status? 422))
        (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "code-1"})
        (has (status? 302))
        ;; Redirect to finish
        (follow-redirect)
        ;; Session created
        (follow-redirect)
        ;; Redirect to pending assessments
        (follow-redirect)
        (has (some-text? "Welcome"))
        (visit "/user/assessments")
        (has (some-text? "AAQ")))))

;; This was a test of the old Always show finish screen property.
;; Probably not needed anymore but keeping anyway.
(deftest registration-flow-no-finish
  (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                reg-service/registration-params (constantly {:allowed?               true
                                                             :fields                 #{:email :sms-number}
                                                             :group                  564616
                                                             :allow-duplicate-email? true
                                                             :allow-duplicate-sms?   true
                                                             :sms-countries          ["se" "gb" "dk" "no" "fi"]
                                                             :auto-username          :none})
                passwords/letters-digits        (constantly "METALLICA")]
    (-> *s*
        (visit "/registration/564610/captcha")
        ;; Captcha session is created
        (follow-redirect)
        (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
        (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
        (visit "/registration/564610/form" :request-method :post :params {:email "brjann@gmail.com" :sms-number "+46070717652"})
        (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
        (has (status? 200))
        (visit "/registration/564610/validate-sms" :request-method :post :params {::code-sms "METALLICA"})
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
        (has (some-text? "Thanks"))
        (visit "/user/assessments")
        ;; Assessments completed
        (follow-redirect)
        ;; Redirect to login screen
        (follow-redirect)
        (has (some-text? "Login")))))

(deftest registration-back-to-registration-at-validation
  (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                reg-service/registration-params (constantly {:allowed?               true
                                                             :fields                 #{:email :sms-number}
                                                             :group                  564616
                                                             :allow-duplicate-email? true
                                                             :allow-duplicate-sms?   true
                                                             :sms-countries          ["se" "gb" "dk" "no" "fi"]
                                                             :auto-username          :none})
                passwords/letters-digits        (constantly "METALLICA")]
    (-> *s*
        (visit "/registration/564610")
        ;; Redirected to info page
        (follow-redirect)
        (visit "/registration/564610/form")
        ;; Redirected to captcha page
        (follow-redirect)
        (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
        (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
        (visit "/registration/564610/form" :request-method :post :params {:email "brjann@gmail.com" :sms-number "+46070717652"})
        (follow-redirect)
        (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
        (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "345345"})
        (has (status? 422))
        (visit "/registration/564610/form")
        (has (status? 200))
        (has (some-text? "Mobile phone"))
        (visit "/registration/564610/validate")
        (has (status? 200)))))

(deftest registration-back-try-to-access-user
  (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                reg-service/registration-params (constantly {:allowed?               true
                                                             :fields                 #{:email :sms-number}
                                                             :group                  564616
                                                             :allow-duplicate-email? true
                                                             :allow-duplicate-sms?   true
                                                             :sms-countries          ["se" "gb" "dk" "no" "fi"]
                                                             :auto-username          :none})
                passwords/letters-digits        (constantly "METALLICA")]
    (-> *s*
        (visit "/registration/564610/captcha")
        ;; Captcha session is created
        (follow-redirect)
        (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
        (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
        (visit "/registration/564610/form" :request-method :post :params {:email "brjann@gmail.com" :sms-number "+46070717652"})
        (follow-redirect)
        (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "345345"})
        (has (status? 422))
        (visit "/user")
        (has (status? 403)))))

(deftest registration-auto-id
  (let [participant-id (reg-service/generate-participant-id 564610 "test-" 4)]
    (with-redefs [captcha/captcha!                    (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params     (constantly {:allowed?               true
                                                                   :fields                 #{:email :sms-number}
                                                                   :group                  564616
                                                                   :allow-duplicate-email? true
                                                                   :allow-duplicate-sms?   true
                                                                   :sms-countries          ["se" "gb" "dk" "no" "fi"]
                                                                   :auto-username          :participant-id
                                                                   :auto-id-prefix         "xxx-"
                                                                   :auto-id-length         3
                                                                   :auto-id?               true})
                  reg-service/generate-participant-id (constantly participant-id)
                  passwords/letters-digits            (constantly "METALLICA")]
      (-> *s*
          (visit "/registration/564610/captcha")
          ;; Captcha session is created
          (follow-redirect)
          (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
          (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
          (visit "/registration/564610/form" :request-method :post :params {:email "brjann@gmail.com" :sms-number "+46070717652"})
          (follow-redirect)
          (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
          (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})))
    (let [by-username       (db/get-user-by-username {:username participant-id})
          by-participant-id (db/get-user-by-participant-id {:participant-id participant-id})]
      (is (= true (map? by-username)))
      (is (= 1 (count by-participant-id))))))

(deftest registration-auto-id-email-username-own-password-no-assessments
  (let [participant-id (reg-service/generate-participant-id 564610 "test-" 4)
        email          (str (apply str (take 20 (repeatedly #(char (+ (rand 26) 65))))) "@example.com")]
    (with-redefs [captcha/captcha!                    (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params     (constantly {:allowed?               true
                                                                   :fields                 #{:email :sms-number :password}
                                                                   :group                  570281 ;;No assessments in this group
                                                                   :allow-duplicate-email? false
                                                                   :allow-duplicate-sms?   true
                                                                   :sms-countries          ["se" "gb" "dk" "no" "fi"]
                                                                   :auto-username          :email
                                                                   :auto-id-prefix         "xxx-"
                                                                   :auto-id-length         3
                                                                   :auto-id?               true})
                  reg-service/generate-participant-id (constantly participant-id)
                  passwords/letters-digits            (constantly "METALLICA")]
      (-> *s*
          (visit "/registration/564610/captcha")
          (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
          (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
          (visit "/registration/564610/form" :request-method :post :params {:email email :sms-number "+46070717652" :password "LEMMY2015xxx"})
          (follow-redirect)
          (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
          (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
          (follow-redirect)
          (has (some-text? email))
          (has (some-text? "chose"))
          (visit "/registration/564610/finished")
          (follow-redirect)
          (has (some-text? "we promise")))
      (let [by-username       (db/get-user-by-username {:username email})
            by-participant-id (db/get-user-by-participant-id {:participant-id participant-id})]
        (is (= true (map? by-username)))
        (is (= 1 (count by-participant-id)))))))

(deftest registration-no-credentials-no-assessments
  (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                reg-service/registration-params (constantly {:allowed?               true
                                                             :fields                 #{:first-name :last-name :email}
                                                             :group                  570281 ;;No assessments in this group
                                                             :allow-duplicate-email? true
                                                             :allow-duplicate-sms?   true
                                                             :sms-countries          ["se" "gb" "dk" "no" "fi"]
                                                             :auto-username          :none
                                                             :auto-id-prefix         "xxx-"
                                                             :auto-id-length         3
                                                             :auto-id?               true})
                passwords/letters-digits        (constantly "METALLICA")]
    (-> *s*
        (visit "/registration/564610/captcha")
        (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
        (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
        (visit "/registration/564610/form" :request-method :post :params {:first-name "Lasse" :last-name "Basse" :email "brjann@gmail.com"})
        (follow-redirect)
        (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
        (follow-redirect)
        (follow-redirect)
        (has (some-text? "we promise")))))

(deftest registration-no-validation-no-credentials-no-assessments
  (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                reg-service/registration-params (constantly {:allowed?               true
                                                             :fields                 #{:first-name :last-name}
                                                             :group                  570281 ;;No assessments in this group
                                                             :allow-duplicate-email? true
                                                             :allow-duplicate-sms?   true
                                                             :sms-countries          ["se" "gb" "dk" "no" "fi"]
                                                             :auto-username          :none
                                                             :auto-id-prefix         "xxx-"
                                                             :auto-id-length         3
                                                             :auto-id?               true})
                passwords/letters-digits        (constantly "METALLICA")]
    (-> *s*
        (visit "/registration/564610/captcha")
        ;; Captcha session is created
        (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
        (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
        (visit "/registration/564610/form" :request-method :post :params {:first-name "Lasse" :last-name "Basse"})
        (follow-redirect)
        (follow-redirect)
        (has (some-text? "we promise")))))

(deftest registration-auto-id-no-prefix-0-length-password
  (let [participant-id (reg-service/generate-participant-id 564610 "" 0)
        password       (passwords/password)]
    (with-redefs [captcha/captcha!                    (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params     (constantly {:allowed?               true
                                                                   :fields                 #{:email :sms-number}
                                                                   :group                  564616
                                                                   :allow-duplicate-email? true
                                                                   :allow-duplicate-sms?   true
                                                                   :sms-countries          ["se" "gb" "dk" "no" "fi"]
                                                                   :auto-username          :participant-id
                                                                   :auto-password?         true
                                                                   :auto-id-prefix         ""
                                                                   :auto-id-length         0
                                                                   :auto-id?               true})
                  reg-service/generate-participant-id (constantly participant-id)
                  passwords/letters-digits            (constantly "METALLICA")
                  passwords/password                  (constantly password)]
      (-> *s*
          (visit "/registration/564610/captcha")
          ;; Captcha session is created
          (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
          (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
          (visit "/registration/564610/form" :request-method :post :params {:email "brjann@gmail.com" :sms-number "+46070717652"})
          (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
          (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
          (follow-redirect)
          (has (some-text? password))
          (has (some-text? participant-id)))
      (let [by-username       (db/get-user-by-username {:username participant-id})
            by-participant-id (db/get-user-by-participant-id {:participant-id participant-id})]
        (is (= true (map? by-username)))
        (is (= 1 (count by-participant-id)))))))

(deftest registration-auto-password
  (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                reg-service/registration-params (constantly {:allowed?               true
                                                             :fields                 #{:email}
                                                             :group                  564616
                                                             :allow-duplicate-email? true
                                                             :auto-username          :participant-id
                                                             :auto-password?         true
                                                             :auto-id-prefix         "xxx-"
                                                             :auto-id-length         4
                                                             :auto-id?               true})
                passwords/letters-digits        (constantly "METALLICA")]
    (let [x        (-> *s*
                       (visit "/registration/564610/captcha")
                       ;; Captcha session is created
                       (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
                       (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
                       (visit "/registration/564610/form" :request-method :post :params {:email "brjann@gmail.com"})
                       (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
                       (follow-redirect))
          username (-> x
                       :enlive
                       (enlive/select [[:span :.username]])
                       (first)
                       :content
                       (first)
                       (string/trim))
          password (-> x
                       :enlive
                       (enlive/select [[:span :.password]])
                       (first)
                       :content
                       (first)
                       (string/trim))]
      (-> *s*
          (visit "/login" :request-method :post :params {:username username :password password})
          (has (status? 302))
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "Welcome"))))))

(deftest registration-duplicate-info
  (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                reg-service/registration-params (constantly {:allowed?               true
                                                             :fields                 #{:email :sms-number}
                                                             :group                  564616
                                                             :allow-duplicate-email? false
                                                             :allow-duplicate-sms?   false
                                                             :sms-countries          ["se" "gb" "dk" "no" "fi"]})
                passwords/letters-digits        (constantly "METALLICA")]
    (-> *s*
        (visit "/registration/564610/captcha")
        ;; Captcha session is created
        (follow-redirect)
        (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
        (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
        (visit "/registration/564610/form" :request-method :post :params {:email "brjann@gmail.com" :sms-number "+46070717652"})
        (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
        (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
        (follow-redirect)
        (has (some-text? "already exists")))))

(deftest registration-captcha-not-created
  (with-redefs [captcha/captcha! (constantly {:filename "xxx" :digits "6666"})]
    (let [response (-> *s*
                       (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"}))]
      (is (string/includes? (get-in response [:response :headers "Location"]) "/registration/564610/captcha")))))

(deftest captcha-timeout
  (let [now (t/now)]
    (with-redefs [captcha/captcha! (constantly {:filename "xxx" :digits "6666"})]
      (let [x (-> *s*
                  (visit "/registration/564610/captcha")
                  ;; Captcha session is created
                  (follow-redirect)
                  (visit "/registration/564610/captcha" :request-method :post :params {:captcha "8888"})
                  (has (status? 422)))]
        (with-redefs [captcha/captcha! (constantly {:filename "xxx" :digits "8888"})]
          (let [x (-> x
                      (visit "/registration/564610/captcha" :request-method :post :params {:captcha "8888"})
                      (has (status? 422)))])
          (with-redefs [t/now (constantly (t/plus now (t/seconds 61)))]
            (-> x
                (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
                ;; Captcha is invalid
                (follow-redirect)
                ;; Captcha session is created
                (follow-redirect)
                (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
                (has (status? 422))
                (visit "/registration/564610/captcha" :request-method :post :params {:captcha "8888"})
                (has (status? 302)))))))))

(deftest captcha-tries
  (let [now (t/now)]
    (with-redefs [captcha/captcha! (constantly {:filename "xxx" :digits "6666"})]
      (let [x (-> *s*
                  (visit "/registration/564610/captcha")
                  ;; Captcha session is created
                  (follow-redirect))]
        (with-redefs [captcha/captcha! (constantly {:filename "xxx" :digits "8888"})]
          (-> x
              (visit "/registration/564610/captcha" :request-method :post :params {:captcha "8888"}) ;; 1
              (has (status? 422))
              (visit "/registration/564610/captcha" :request-method :post :params {:captcha "8888"}) ;; 2
              (has (status? 422))
              (visit "/registration/564610/captcha" :request-method :post :params {:captcha "8888"}) ;; 3
              (has (status? 422))
              (visit "/registration/564610/captcha" :request-method :post :params {:captcha "8888"}) ;; 4
              (has (status? 422))
              (visit "/registration/564610/captcha" :request-method :post :params {:captcha "8888"}) ;; 5
              (has (status? 302))
              ;; Captcha is invalid
              (follow-redirect)
              ;; New captcha created
              (follow-redirect)
              (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"}) ;; 1
              (has (status? 422))
              (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"}) ;; 2
              (has (status? 422))
              (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"}) ;; 3
              (has (status? 422))
              (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"}) ;; 4
              (has (status? 422))
              (visit "/registration/564610/captcha" :request-method :post :params {:captcha "8888"}) ;; 5 - correct
              ;; Correct captcha, redirected to privacy page.
              (follow-redirect)
              (has (some-text? "Who is collecting"))))))))

(deftest registration-privacy-notice-disabled
  (with-redefs [captcha/captcha!                         (constantly {:filename "xxx" :digits "6666"})
                privacy-service/privacy-notice-disabled? (constantly true)
                reg-service/registration-params          (constantly {:allowed?               true
                                                                      :fields                 #{:email :sms-number}
                                                                      :group                  564616
                                                                      :allow-duplicate-email? true
                                                                      :allow-duplicate-sms?   true
                                                                      :sms-countries          ["se" "gb" "dk" "no" "fi"]
                                                                      :auto-username          :none})
                passwords/letters-digits                 (constantly "METALLICA")]
    (-> *s*
        (visit "/registration/564610/captcha")
        ;; Captcha session is created
        (follow-redirect)
        (has (some-text? "code below"))
        (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
        (has (status? 302))
        (visit "/registration/564610/privacy")
        (has (status? 302))
        (visit "/registration/564610/form")
        (has (status? 200))
        (has (some-text? "Enter your"))
        (visit "/registration/564610/form" :request-method :post :params {:email "brjann@gmail.com" :sms-number "+46070717652"})
        (has (status? 302))
        (pass-by (messages-are?
                   [[:email "METALLICA"]
                    [:sms "METALLICA"]]
                   (poll-message-chan *debug-chan* 2)))
        (follow-redirect)
        (has (some-text? "Validate"))
        (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
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
        (has (some-text? "Thanks"))
        (visit "/user/assessments")
        ;; Assessments completed
        (follow-redirect)
        ;; Redirect to finish screen
        (follow-redirect)
        (has (some-text? "Login")))))

(deftest registration-all-fields-sql-query-no-assessments
  (let [email (str (apply str (take 20 (repeatedly #(char (+ (rand 26) 65))))) "@example.com")]
    (with-redefs [captcha/captcha!         (constantly {:filename "xxx" :digits "6666"})
                  db/registration-params   (constantly {:allowed?               true,
                                                        :allow-duplicate-sms?   true,
                                                        :group                  570281,
                                                        :sms-countries          "se",
                                                        :fields                 "a:6:{s:5:\"Email\";b:1;s:9:\"FirstName\";b:1;s:8:\"LastName\";b:1;s:12:\"Personnummer\";b:1;s:9:\"SMSNumber\";b:1;s:8:\"Password\";b:1;}",
                                                        :bankid-change-names?   false,
                                                        :auto-username          "email",
                                                        :bankid?                false,
                                                        :study-consent?         false,
                                                        :auto-id-length         3,
                                                        :allow-duplicate-email? false,
                                                        :auto-id-prefix         "xxx-"})
                  passwords/letters-digits (constantly "METALLICA")]
      (-> *s*
          (visit "/registration/564610/captcha")
          (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
          (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
          (visit "/registration/564610/form" :request-method :post :params {:first-name "Lemmy"
                                                                            :last-name  "Kilmister"
                                                                            :pid-number "19451224-6666"
                                                                            :email      email
                                                                            :sms-number "+46070717652"
                                                                            :password   "LEMMY2015xxx"})
          (follow-redirect)
          (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
          (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
          (follow-redirect)
          (has (some-text? email))
          (has (some-text? "chose"))
          (visit "/registration/564610/finished")
          (follow-redirect)
          (has (some-text? "we promise")))
      (let [by-username (db/get-user-by-username {:username email})]
        (is (= true (map? by-username))))
      (-> *s*
          (visit "/login" :request-method :post :params {:username email :password "LEMMY2015xxx"})
          (has (status? 302))
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "no active tasks"))))))

(deftest already-logged-in
  (-> *s*
      (modify-session {:user-id 536975 :double-authed? true})
      (visit "/user/tx/messages")
      (has (status? 200))
      (visit "/registration/564610")
      (follow-redirect)
      (follow-redirect)
      (has (some-text? "Already"))
      (visit "/registration/564610/logout")
      (follow-redirect)
      (has (some-text? "Welcome"))))