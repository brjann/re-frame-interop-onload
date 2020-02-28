(ns bass4.test.reqs-registration
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [clojure.core.async :refer [chan dropping-buffer]]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.assessment-utils :refer :all]
            [bass4.test.core :refer :all]
            [bass4.captcha :as captcha]
            [bass4.config :refer [env]]
            [bass4.db.core :as db]
            [clj-time.core :as t]
            [clojure.string :as string]
            [bass4.registration.services :as reg-service]
            [bass4.external-messages.async :refer [*debug-chan*]]
            [bass4.passwords :as passwords]
            [net.cgrand.enlive-html :as enlive]
            [bass4.services.privacy :as privacy-service]
            [bass4.session.timeout :as session-timeout]
            [bass4.services.user :as user-service]
            [bass4.registration.responses :as reg-response]
            [clojure.tools.logging :as log]))


(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(use-fixtures
  :each
  random-date-tz-fixture
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

(defn create-registration-group!
  ([project assessment-series] (create-registration-group! project assessment-series [286]))
  ([project assessment-series instruments]
   (let [reg-assessment (create-assessment! assessment-series
                                            {"Scope"        1
                                             "WelcomeText"  "Welcome"
                                             "ThankYouText" "Thanks"})
         reg-group      (create-group! project)]
     (doseq [instrument-id instruments]
       (link-instrument! reg-assessment instrument-id))     ; AAQ
     (create-group-administration! reg-group reg-assessment 1 {:date (midnight (t/now))})
     reg-group)))

(deftest registration-flow+renew
  (fix-time
    (let [reg-group         (create-registration-group! project-reg-allowed project-reg-allowed-ass-series)
          timeout-hard      (session-timeout/timeout-hard-limit)
          timeout-hard-soon (session-timeout/timeout-hard-soon-limit)]
      (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                    reg-service/registration-params (constantly {:allowed?               true
                                                                 :fields                 #{:email :sms-number}
                                                                 :group                  reg-group
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
            (visit "/api/session/timeout-hard-soon")
            (visit "/api/session/status")
            (has (api-response? {:hard    timeout-hard-soon
                                 :re-auth nil}))
            (visit "/api/session/renew")
            (has (status? 200))
            (visit "/api/session/status")
            (has (api-response? {:hard    timeout-hard
                                 :re-auth nil}))
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
            (visit "/api/logout-path")
            (has (some-text? "/registration/564610"))
            (visit "/user/assessments")
            (has (some-text? "AAQ"))
            (visit "/api/session/timeout-hard-soon")
            (visit "/api/session/status")
            (has (api-response? {:hard    timeout-hard-soon
                                 :re-auth nil}))
            (visit "/api/session/renew")
            (has (status? 200))
            (visit "/api/session/status")
            (has (api-response? {:hard    timeout-hard
                                 :re-auth nil}))
            (visit "/user/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
            (follow-redirect)
            (has (some-text? "Thanks"))
            (visit "/user/assessments")
            ;; Assessments completed
            (follow-redirect)
            ;; Redirect to activities finished redirect
            (follow-redirect)
            ;; Redirect to activities finished screen
            (follow-redirect)
            (has (some-text? "finished")))))))

(deftest registration-no-study-consent
  (let [reg-group (create-registration-group! project-reg-allowed project-reg-allowed-ass-series)]
    (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params (constantly {:allowed?               true
                                                               :fields                 #{:email :sms-number}
                                                               :group                  reg-group
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
          (has (status? 400))))))

(deftest registration-change-sms
  (let [reg-group (create-registration-group! project-reg-allowed project-reg-allowed-ass-series)]
    (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params (constantly {:allowed?               true
                                                               :fields                 #{:email :sms-number}
                                                               :group                  reg-group
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
          (has (some-text? "AAQ"))))))

(deftest registration-back-to-registration-at-validation
  (let [reg-group (create-registration-group! project-reg-allowed project-reg-allowed-ass-series)]
    (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params (constantly {:allowed?               true
                                                               :fields                 #{:email :sms-number}
                                                               :group                  reg-group
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
          (has (status? 200))))))

(deftest registration-back-try-to-access-user
  (let [reg-group (create-registration-group! project-reg-allowed project-reg-allowed-ass-series)]
    (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params (constantly {:allowed?               true
                                                               :fields                 #{:email :sms-number}
                                                               :group                  reg-group
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
          (has (status? 403))))))

(deftest registration-auto-id
  (let [participant-id (reg-service/generate-participant-id 564610 "test-" 4)
        reg-group      (create-registration-group! project-reg-allowed project-reg-allowed-ass-series)]
    (with-redefs [captcha/captcha!                    (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params     (constantly {:allowed?               true
                                                                   :fields                 #{:email :sms-number}
                                                                   :group                  reg-group
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
        email          (str (apply str (take 20 (repeatedly #(char (+ (rand 26) 65))))) "@example.com")
        reg-group      (create-group! project-reg-allowed)]
    (with-redefs [captcha/captcha!                    (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params     (constantly {:allowed?               true
                                                                   :fields                 #{:email :sms-number :password}
                                                                   :group                  reg-group ;;No assessments in this group
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
          (has (not-text? "interrupted"))
          (visit "/registration/564610/finished")
          (follow-redirect)
          (has (some-text? "we promise")))
      (let [by-username       (db/get-user-by-username {:username email})
            by-participant-id (db/get-user-by-participant-id {:participant-id participant-id})]
        (is (= true (map? by-username)))
        (is (= 1 (count by-participant-id)))))))

(deftest registration-auto-id-email-username-own-password-with-assessments
  (let [participant-id (reg-service/generate-participant-id 564610 "test-" 4)
        email          (str (apply str (take 20 (repeatedly #(char (+ (rand 26) 65))))) "@example.com")
        reg-group      (create-registration-group! project-reg-allowed project-reg-allowed-ass-series)]
    (with-redefs [captcha/captcha!                    (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params     (constantly {:allowed?               true
                                                                   :fields                 #{:email :sms-number :password}
                                                                   :group                  reg-group
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
          (has (some-text? "interrupted"))
          (has (some-text? "login address"))))))

(deftest registration-no-credentials-no-assessments
  (let [reg-group (create-group! project-reg-allowed)]
    (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params (constantly {:allowed?               true
                                                               :fields                 #{:first-name :last-name :email}
                                                               :group                  reg-group ;;No assessments in this group
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
          (has (some-text? "we promise"))))))

(deftest registration-no-validation-no-credentials-no-assessments
  (let [reg-group (create-group! project-reg-allowed)]
    (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params (constantly {:allowed?               true
                                                               :fields                 #{:first-name :last-name}
                                                               :group                  reg-group ;;No assessments in this group
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
          (has (some-text? "we promise"))))))

(deftest registration-auto-id-no-prefix-0-length-password
  (let [reg-group      (create-registration-group! project-reg-allowed project-reg-allowed-ass-series)
        participant-id (reg-service/generate-participant-id 564610 "" 0)
        password       (passwords/password)]
    (with-redefs [captcha/captcha!                    (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params     (constantly {:allowed?               true
                                                                   :fields                 #{:email :sms-number}
                                                                   :group                  reg-group
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
  (let [reg-group (create-registration-group! project-reg-allowed project-reg-allowed-ass-series)]
    (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params (constantly {:allowed?               true
                                                               :fields                 #{:email}
                                                               :group                  reg-group
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
            (has (some-text? "Welcome")))))))

(deftest registration-duplicate-info
  (let [reg-group (create-group! project-reg-allowed)]
    (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params (constantly {:allowed?               true
                                                               :fields                 #{:email :sms-number}
                                                               :group                  reg-group
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
          (has (some-text? "already exists"))))))

;; ----------------------
;;   DUPLICATES TESTING
;; ----------------------

(def sms-email-counter (atom 0))
(defn random-sms []
  (swap! sms-email-counter inc)
  (str "+46" (System/currentTimeMillis) @sms-email-counter))

(defn random-email []
  (swap! sms-email-counter inc)
  (str (System/currentTimeMillis) @sms-email-counter "@example.com"))

(deftest handle-duplicates
  (let [resolve-duplicate @#'reg-response/resolve-duplicate]
    (is (= [:duplicate :no-resume]
           (resolve-duplicate {:sms-number "666"
                               :email      "brjann"
                               :username   ""
                               :password   ""
                               :group      666}
                              {:sms-number "666"
                               :email      "brjann"}
                              {:allow-resume?          false
                               :allow-duplicate-sms?   false
                               :allow-duplicate-email? false})))
    (is (= [:resume :ok]
           (resolve-duplicate {:sms-number "666"
                               :email      "brjann"
                               :username   ""
                               :password   ""
                               :group      666}
                              {:sms-number "666"
                               :email      "brjann"}
                              {:allow-resume?          true
                               :allow-duplicate-sms?   false
                               :allow-duplicate-email? false
                               :group                  666})))
    (is (= [:resume :ok]
           (resolve-duplicate {:sms-number "555"
                               :email      "brjann"
                               :username   ""
                               :password   ""
                               :group      666}
                              {:sms-number "666"
                               :email      "brjann"}
                              {:allow-resume?          true
                               :allow-duplicate-sms?   true
                               :allow-duplicate-email? false
                               :group                  666})))
    (is (= [:resume :ok]
           (resolve-duplicate {:sms-number "666"
                               :email      "brjann"
                               :username   ""
                               :password   ""
                               :group      666}
                              {:sms-number "666"
                               :email      "ljotsson"}
                              {:allow-resume?          true
                               :allow-duplicate-sms?   false
                               :allow-duplicate-email? true
                               :group                  666})))
    (is (= [:resume :ok]
           (resolve-duplicate {:sms-number "555"
                               :pid-number "19121212-1212"
                               :email      "brjann"
                               :username   ""
                               :password   ""
                               :group      666}
                              {:sms-number "555"
                               :email      "brjann"
                               :pid-number "19121212-1212"}
                              {:allow-resume?           true
                               :allow-duplicate-sms?    false
                               :allow-duplicate-email?  false
                               :allow-duplicate-bankid? false
                               :bankid?                 true
                               :group                   666})))
    (is (= [:resume :ok]
           (resolve-duplicate {:sms-number "555"
                               :pid-number "19121212-1212"
                               :email      "brjann"
                               :username   ""
                               :password   ""
                               :group      666}
                              {:sms-number "666"
                               :email      "illugi"
                               :pid-number "19121212-1212"}
                              {:allow-resume?           true
                               :allow-duplicate-sms?    true
                               :allow-duplicate-email?  true
                               :allow-duplicate-bankid? false
                               :bankid?                 true
                               :group                   666})))
    (is (= [:duplicate #{:sms-mismatch}]
           (resolve-duplicate {:sms-number "555"
                               :email      "brjann"
                               :username   ""
                               :password   ""
                               :group      666}
                              {:sms-number "666"
                               :email      "brjann"}
                              {:allow-resume?          true
                               :allow-duplicate-sms?   false
                               :allow-duplicate-email? false
                               :group                  666})))
    (is (= [:duplicate #{:pid-mismatch}]
           (resolve-duplicate {:sms-number "555"
                               :pid-number "19121212-1212"
                               :email      "brjann"
                               :username   ""
                               :password   ""
                               :group      666}
                              {:sms-number "555"
                               :email      "brjann"
                               :pid-number "19131313-1313"}
                              {:allow-resume?           true
                               :allow-duplicate-sms?    false
                               :allow-duplicate-email?  false
                               :allow-duplicate-bankid? false
                               :bankid?                 true
                               :group                   666})))
    (is (= [:duplicate #{:email-mismatch}]
           (resolve-duplicate {:sms-number "666"
                               :email      "ljotsson"
                               :username   ""
                               :password   ""
                               :group      666}
                              {:sms-number "666"
                               :email      "brjann"}
                              {:allow-resume?          true
                               :allow-duplicate-sms?   false
                               :allow-duplicate-email? false
                               :group                  666})))
    (is (= [:duplicate #{:sms-mismatch}]
           (resolve-duplicate {:sms-number "555"
                               :email      "brjann"
                               :username   ""
                               :password   ""
                               :group      666}
                              {:sms-number "666"
                               :email      "brjann"}
                              {:allow-resume?          true
                               :allow-duplicate-sms?   false
                               :allow-duplicate-email? true
                               :group                  666})))
    (is (= [:duplicate #{:email-mismatch}]
           (resolve-duplicate {:sms-number "666"
                               :email      "brjann"
                               :username   ""
                               :password   ""
                               :group      666}
                              {:sms-number "666"
                               :email      "ljotsson"}
                              {:allow-resume?          true
                               :allow-duplicate-sms?   true
                               :allow-duplicate-email? false
                               :group                  666})))
    (is (= [:duplicate #{:email-mismatch :sms-mismatch}]
           (resolve-duplicate {:sms-number "666"
                               :email      "brjann"
                               :pid-number "19121212-1212"
                               :username   ""
                               :password   ""
                               :group      666}
                              {:sms-number "555"
                               :pid-number "19121212-1212"
                               :email      "ljotsson"}
                              {:allow-resume?           true
                               :allow-duplicate-sms?    false
                               :allow-duplicate-email?  false
                               :allow-duplicate-bankid? false
                               :bankid                  true
                               :group                   666})))
    (is (= [:duplicate :group-mismatch]
           (resolve-duplicate {:sms-number "666"
                               :email      "brjann"
                               :username   ""
                               :password   ""
                               :group      555}
                              {:sms-number "666"
                               :email      "brjann"}
                              {:allow-resume?          true
                               :allow-duplicate-sms?   false
                               :allow-duplicate-email? false
                               :group                  666})))
    (is (= [:duplicate :group-mismatch]
           (resolve-duplicate {:sms-number "666"
                               :email      "brjann"
                               :username   ""
                               :password   ""
                               :group      nil}
                              {:sms-number "666"
                               :email      "brjann"}
                              {:allow-resume?          true
                               :allow-duplicate-sms?   false
                               :allow-duplicate-email? false
                               :group                  nil})))
    (is (= :thrown (try
                     (resolve-duplicate {:group 10}
                                        {}
                                        {:group                   10
                                         :allow-resume?           true
                                         :allow-duplicate-sms?    true
                                         :allow-duplicate-email?  true
                                         :allow-duplicate-bankid? true})
                     (catch Exception _
                       :thrown))))))

(deftest registration-duplicate-info-too-many
  (let [sms-number (random-sms)
        email      (random-email)]
    (user-service/create-user! 564610 {:SMSNumber sms-number
                                       :Email     email
                                       :group     564616})
    (user-service/create-user! 564610 {:SMSNumber sms-number
                                       :Email     email
                                       :group     564616})
    (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params (constantly {:allowed?               true
                                                               :fields                 #{:email :sms-number}
                                                               :group                  nil
                                                               :allow-duplicate-email? true
                                                               :allow-duplicate-sms?   false
                                                               :sms-countries          ["se"]
                                                               :allow-resume?          true})
                  passwords/letters-digits        (constantly "METALLICA")]
      (-> *s*
          (visit "/registration/564610/captcha")
          ;; Captcha session is created
          (follow-redirect)
          (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
          (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
          (visit "/registration/564610/form" :request-method :post :params {:email email :sms-number sms-number})
          (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
          (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
          (follow-redirect)
          (has (some-text? "already exists"))))))

(deftest registration-duplicate-resume-allowed-no-assessments
  (let [sms-number (random-sms)
        email      (random-email)
        reg-group  (create-group! project-reg-allowed)]
    (user-service/create-user! 564610 {:SMSNumber sms-number
                                       :Email     email
                                       :group     reg-group})
    (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params (constantly {:allowed?               true
                                                               :fields                 #{:email :sms-number}
                                                               :group                  reg-group
                                                               :allow-duplicate-email? false
                                                               :allow-duplicate-sms?   false
                                                               :sms-countries          ["se"]
                                                               :allow-resume?          true})
                  passwords/letters-digits        (constantly "METALLICA")]
      (-> *s*
          (visit "/registration/564610/captcha")
          ;; Captcha session is created
          (follow-redirect)
          (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
          (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
          (visit "/registration/564610/form" :request-method :post :params {:email email :sms-number sms-number})
          (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
          (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
          (follow-redirect)
          (follow-redirect)
          (has (some-text? "Registration already completed"))))))

(deftest registration-duplicate-resume-allowed-assessments
  (let [sms-number (random-sms)
        email      (random-email)
        reg-group  (create-registration-group! project-reg-allowed project-reg-allowed-ass-series [286 4743])]
    (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params (constantly {:allowed?               true
                                                               :fields                 #{:email :sms-number}
                                                               :group                  reg-group
                                                               :allow-duplicate-email? false
                                                               :allow-duplicate-sms?   false
                                                               :sms-countries          ["se"]
                                                               :allow-resume?          true
                                                               :auto-username          :none})
                  passwords/letters-digits        (constantly "METALLICA")]
      (-> *s*
          (visit "/registration/564610/captcha")
          ;; Captcha session is created
          (follow-redirect)
          (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
          (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
          (visit "/registration/564610/form" :request-method :post :params {:email email :sms-number sms-number})
          (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
          (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
          (has (status? 302))
          ;; Redirect to finish
          (follow-redirect)
          ;; Session created
          (follow-redirect)
          (has (some-text? "exact"))
          (visit "/api/logout-path")
          (has (some-text? "/registration/564610"))
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
          (visit "/registration/564610/captcha")
          ;; Captcha session is created
          (follow-redirect)
          (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
          (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
          (visit "/registration/564610/form" :request-method :post :params {:email email :sms-number sms-number})
          (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
          (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
          ;; Redirect to finish
          (follow-redirect)
          ;; Session created
          (follow-redirect)
          (has (some-text? "Continue registration"))
          (has (some-text? "exact"))
          (visit "/api/logout-path")
          (has (some-text? "/registration/564610"))
          (visit "/user")
          ;; Redirect to pending assessments
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/user/assessments")
          (has (some-text? "Agoraphobic"))
          (visit "/user/assessments" :request-method :post :params {:instrument-id 4743 :items "{}" :specifications "{}"})
          (follow-redirect)
          (has (some-text? "Thanks"))))))

(deftest registration-duplicate-resume-allowed-assessments-with-auto-password
  (let [sms-number (random-sms)
        email      (random-email)
        password1  (str (passwords/password) "1")
        password2  (str (passwords/password) "2")
        reg-group  (create-registration-group! project-reg-allowed project-reg-allowed-ass-series [286 4743])]
    (is (false? (= password1 password2)))
    (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params (constantly {:allowed?               true
                                                               :fields                 #{:email :sms-number}
                                                               :group                  reg-group
                                                               :auto-username          :email
                                                               :auto-password?         true
                                                               :allow-duplicate-email? false
                                                               :allow-duplicate-sms?   false
                                                               :sms-countries          ["se"]
                                                               :allow-resume?          true})
                  passwords/letters-digits        (constantly "METALLICA")]
      (with-redefs [passwords/password (constantly password1)]
        (-> *s*
            (visit "/registration/564610/captcha")
            ;; Captcha session is created
            (follow-redirect)
            (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
            (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
            (visit "/registration/564610/form" :request-method :post :params {:email email :sms-number sms-number})
            (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
            (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
            (follow-redirect)
            (has (some-text? password1))
            (has (some-text? email))
            (visit "/registration/564610/finished")
            ;; Session created
            (follow-redirect)
            ;; Redirect to pending assessments
            (follow-redirect)
            (has (some-text? "Welcome"))
            (visit "/api/logout-path")
            (has (some-text? "/login"))
            (visit "/user/assessments")
            (has (some-text? "AAQ"))
            (visit "/user/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
            (follow-redirect)
            (visit "/api/session/timeout-hard")
            (visit "/user/assessments")
            (has (status? 403))))
      (-> *s*
          (visit "/login" :request-method :post :params {:username email :password password1})
          (has (status? 302))
          (visit "/login" :request-method :post :params {:username email :password password2})
          (has (status? 422)))
      (with-redefs [passwords/password (constantly password2)]
        (-> *s*
            (visit "/registration/564610/captcha")
            ;; Captcha session is created
            (follow-redirect)
            (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
            (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
            (visit "/registration/564610/form" :request-method :post :params {:email email :sms-number sms-number})
            (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
            (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
            (follow-redirect)
            (has (some-text? password2))
            (has (some-text? email))
            (visit "/registration/564610/finished")
            ;; Session created
            (follow-redirect)
            (has (some-text? "Continue registration"))
            (has (not-text? "exact"))
            (visit "/user")
            ;; Redirect to pending assessments
            (follow-redirect)
            (has (some-text? "Welcome"))
            (visit "/user/assessments")
            (has (some-text? "Agoraphobic"))
            (visit "/user/assessments" :request-method :post :params {:instrument-id 4743 :items "{}" :specifications "{}"})
            (follow-redirect)
            (has (some-text? "Thanks"))))
      (-> *s*
          (visit "/login" :request-method :post :params {:username email :password password1})
          (has (status? 422))
          (visit "/login" :request-method :post :params {:username email :password password2})
          (has (status? 302))))))

(deftest registration-duplicate-resume-allowed-assessments-with-custom-password
  (let [sms-number (random-sms)
        email      (random-email)
        password1  (str (passwords/password) "X1")
        password2  (str (passwords/password) "X2")
        reg-group  (create-registration-group! project-reg-allowed project-reg-allowed-ass-series [286 4743])]
    (is (false? (= password1 password2)))
    (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params (constantly {:allowed?               true
                                                               :fields                 #{:email :sms-number :password}
                                                               :group                  reg-group
                                                               :auto-username          :email
                                                               :allow-duplicate-email? false
                                                               :allow-duplicate-sms?   false
                                                               :sms-countries          ["se"]
                                                               :allow-resume?          true})
                  passwords/letters-digits        (constantly "METALLICA")]
      (with-redefs [passwords/password (constantly password1)]
        (-> *s*
            (visit "/registration/564610/captcha")
            ;; Captcha session is created
            (follow-redirect)
            (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
            (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
            (visit "/registration/564610/form" :request-method :post :params {:email email :sms-number sms-number :password password1})
            (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
            (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
            (follow-redirect)
            (has (some-text? "you chose"))
            (has (some-text? email))
            (visit "/registration/564610/finished")
            ;; Session created
            (follow-redirect)
            ;; Redirect to pending assessments
            (follow-redirect)
            (has (some-text? "Welcome"))
            (visit "/user/assessments")
            (has (some-text? "AAQ"))
            (visit "/user/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
            (follow-redirect)
            (visit "/api/session/timeout-hard")
            (visit "/user/assessments")
            (has (status? 403))))
      (-> *s*
          (visit "/login" :request-method :post :params {:username email :password password1})
          (has (status? 302))
          (visit "/login" :request-method :post :params {:username email :password password2})
          (has (status? 422)))
      (with-redefs [passwords/password (constantly password2)]
        (-> *s*
            (visit "/registration/564610/captcha")
            ;; Captcha session is created
            (follow-redirect)
            (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
            (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
            (visit "/registration/564610/form" :request-method :post :params {:email email :sms-number sms-number :password password2})
            (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
            (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
            (follow-redirect)
            (has (some-text? "you chose"))
            (has (some-text? email))
            (visit "/registration/564610/finished")
            ;; Session created
            (follow-redirect)
            (has (some-text? "Continue registration"))
            (has (not-text? "exact"))
            (visit "/user")
            ;; Redirect to pending assessments
            (follow-redirect)
            (has (some-text? "Welcome"))
            (visit "/user/assessments")
            (has (some-text? "Agoraphobic"))
            (visit "/user/assessments" :request-method :post :params {:instrument-id 4743 :items "{}" :specifications "{}"})
            (follow-redirect)
            (has (some-text? "Thanks"))))
      (-> *s*
          (visit "/login" :request-method :post :params {:username email :password password1})
          (has (status? 422))
          (visit "/login" :request-method :post :params {:username email :password password2})
          (has (status? 302))))))

(deftest registration-duplicate-login-resume-not-allowed
  (let [sms-number (random-sms)
        email      (random-email)
        reg-group  (create-registration-group! project-reg-allowed project-reg-allowed-ass-series)]
    (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params (constantly {:allowed?               true
                                                               :auto-username          :none
                                                               :fields                 #{:email :sms-number}
                                                               :group                  reg-group
                                                               :allow-duplicate-email? false
                                                               :allow-duplicate-sms?   false
                                                               :allow-resume?          false
                                                               :sms-countries          ["se"]})
                  passwords/letters-digits        (constantly "METALLICA")]
      (-> *s*
          (visit "/registration/564610/captcha")
          ;; Captcha session is created
          (follow-redirect)
          (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
          (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
          (visit "/registration/564610/form" :request-method :post :params {:email email :sms-number sms-number})
          (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
          (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
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
          (visit "/api/session/timeout-hard")
          (visit "/user/assessments")
          (has (status? 403)))
      (-> *s*
          (visit "/registration/564610/captcha")
          ;; Captcha session is created
          (follow-redirect)
          (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
          (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
          (visit "/registration/564610/form" :request-method :post :params {:email email :sms-number sms-number})
          (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
          (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
          ;; Redirect to finish
          (follow-redirect)
          (has (some-text? "already exists"))))))

(deftest registration-duplicate-login-resume-mismatch
  (let [sms-number (random-sms)
        email      (random-email)
        reg-group  (create-registration-group! project-reg-allowed project-reg-allowed-ass-series)]
    (user-service/create-user! 564610 {:SMSNumber sms-number
                                       :Email     "brjann@gmail.com"
                                       :group     reg-group
                                       :password  "xxx"
                                       :username  "xxx"})
    (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params (constantly {:allowed?               true
                                                               :fields                 #{:email :sms-number}
                                                               :group                  reg-group
                                                               :allow-duplicate-email? false
                                                               :allow-duplicate-sms?   false
                                                               :allow-resume?          true
                                                               :sms-countries          ["se"]})
                  passwords/letters-digits        (constantly "METALLICA")]
      (-> *s*
          (visit "/registration/564610/captcha")
          ;; Captcha session is created
          (follow-redirect)
          (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
          (visit "/registration/564610/privacy" :request-method :post :params {:i-consent "i-consent"})
          (visit "/registration/564610/form" :request-method :post :params {:email email :sms-number sms-number})
          (visit "/registration/564610/validate-email" :request-method :post :params {:code-email "METALLICA"})
          (visit "/registration/564610/validate-sms" :request-method :post :params {:code-sms "METALLICA"})
          (follow-redirect)
          (has (some-text? "already exists"))))))


;; ----------------------
;;   CAPTCHA TESTING
;; ----------------------


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
  (let [reg-group (create-registration-group! project-reg-allowed project-reg-allowed-ass-series)]
    (with-redefs [captcha/captcha!                         (constantly {:filename "xxx" :digits "6666"})
                  privacy-service/privacy-notice-disabled? (constantly true)
                  reg-service/registration-params          (constantly {:allowed?               true
                                                                        :fields                 #{:email :sms-number}
                                                                        :group                  reg-group
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
          (has (some-text? "Thanks"))))))

(deftest registration-all-fields-sql-query-no-assessments
  (let [email (str (apply str (take 20 (repeatedly #(char (+ (rand 26) 65))))) "@example.com")]
    (with-redefs [captcha/captcha!         (constantly {:filename "xxx" :digits "6666"})
                  db/registration-params   (constantly {:allowed?               true,
                                                        :allow-duplicate-sms?   true,
                                                        :group                  nil,
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
          (follow-redirect)
          (has (some-text? "No activities"))))))

(deftest already-logged-in
  (let [user-id (create-user-with-treatment! tx-autoaccess)]
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/user/tx/messages")
        (has (status? 200))
        (visit "/registration/564610")
        (follow-redirect)
        (follow-redirect)
        (has (some-text? "Already"))
        (visit "/registration/564610/clear-session")
        (follow-redirect)
        (has (some-text? "Welcome")))))

(deftest session-has-data
  ;; Doesn't have data - 1 redirect
  (-> *s*
      (visit "/registration/564610")
      (follow-redirect)
      (has (some-text? "Welcome")))
  ;; Does have data - 3 redirects
  (-> *s*
      (modify-session {"xxx" true})
      (visit "/registration/564610")
      (follow-redirect)
      (follow-redirect)
      (follow-redirect)
      (has (some-text? "Welcome"))))