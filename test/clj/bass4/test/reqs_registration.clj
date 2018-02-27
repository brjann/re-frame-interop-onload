(ns bass4.test.reqs-registration
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures debug-headers-text? log-return]]
            [bass4.captcha :as captcha]
            [bass4.config :refer [env]]
            [bass4.db.core :as db]
            [bass4.services.auth :as auth-service]
            [bass4.services.user :as user]
            [bass4.middleware.debug :as debug]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [clojure.string :as string]
            [bass4.services.registration :as reg-service]
            [bass4.passwords :as passwords]))


(use-fixtures
  :once
  test-fixtures)


(deftest registration-not-allowed
  (-> (session (app))
      (visit "/registration/564612")
      (has (some-text? "Registration not allowed"))))

(deftest registration-flow
  (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                reg-service/registration-params (constantly {:fields                 #{:email :sms-number}
                                                             :group                  564616
                                                             :allow-duplicate-email? true
                                                             :allow-duplicate-sms?   true
                                                             :sms-countries          ["se" "gb" "dk" "no" "fi"]
                                                             :auto-username          :none})
                auth-service/letters-digits     (constantly "METALLICA")]
    (-> (session (app))
        (visit "/registration/564610")
        ;; Captcha session is created
        (follow-redirect)
        ;; Redirected do captcha page
        (follow-redirect)
        (has (some-text? "code below"))
        (visit "/registration/564610/captcha" :request-method :post :params {:captcha "234234"})
        (has (status? 422))
        (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
        (has (status? 302))
        (visit "/registration/564610/captcha")
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "Welcome"))
        (visit "/registration/564610" :request-method :post :params {:captcha "234234"})
        (has (status? 400))
        (visit "/registration/564610" :request-method :post :params {:email "brjann@gmail.com"})
        (has (status? 400))
        (visit "/registration/564610" :request-method :post :params {:email "brjann@gmail.com" :sms-number "+11343454354"})
        (has (status? 422))
        (visit "/registration/564610" :request-method :post :params {:email "brjann@gmail.com" :sms-number "+46070717652"})
        (has (status? 302))
        (debug-headers-text? "MAIL" "SMS" "METALLICA")
        (follow-redirect)
        (has (some-text? "Validate"))
        (visit "/registration/564610/validate" :request-method :post :params {:code-email "3434"})
        (has (status? 400))
        (visit "/registration/564610")
        (visit "/registration/564610/validate" :request-method :post :params {:code-sms "3434"})
        (has (status? 400))
        (visit "/registration/564610/validate" :request-method :post :params {:code-email "3434" :code-sms "345345"})
        (has (status? 422))
        (visit "/registration/564610/validate" :request-method :post :params {:code-email "METALLICA" :code-sms "345345"})
        (has (status? 422))
        (visit "/registration/564610/validate" :request-method :post :params {:code-email "METALLICA" :code-sms "METALLICA"})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "AAQ")))))

(deftest registration-back-to-registration-at-validation
  (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                reg-service/registration-params (constantly {:fields                 #{:email :sms-number}
                                                             :group                  564616
                                                             :allow-duplicate-email? true
                                                             :allow-duplicate-sms?   true
                                                             :sms-countries          ["se" "gb" "dk" "no" "fi"]
                                                             :auto-username          :none})
                auth-service/letters-digits     (constantly "METALLICA")]
    (-> (session (app))
        (visit "/registration/564610")
        ;; Captcha session is created
        (follow-redirect)
        ;; Redirected do captcha page
        (follow-redirect)
        (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
        (follow-redirect)
        (has (some-text? "Welcome"))
        (visit "/registration/564610" :request-method :post :params {:email "brjann@gmail.com" :sms-number "+46070717652"})
        (follow-redirect)
        (visit "/registration/564610/validate" :request-method :post :params {:code-email "METALLICA" :code-sms "345345"})
        (has (status? 422))
        (visit "/registration/564610")
        (follow-redirect)
        (follow-redirect)
        (visit "/registration/564610/validate")
        (has (status? 403)))))

(deftest registration-back-try-to-access-user
  (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                reg-service/registration-params (constantly {:fields                 #{:email :sms-number}
                                                             :group                  564616
                                                             :allow-duplicate-email? true
                                                             :allow-duplicate-sms?   true
                                                             :sms-countries          ["se" "gb" "dk" "no" "fi"]
                                                             :auto-username          :none})
                auth-service/letters-digits     (constantly "METALLICA")]
    (-> (session (app))
        (visit "/registration/564610")
        ;; Captcha session is created
        (follow-redirect)
        ;; Redirected do captcha page
        (follow-redirect)
        (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
        (follow-redirect)
        (has (some-text? "Welcome"))
        (visit "/registration/564610" :request-method :post :params {:email "brjann@gmail.com" :sms-number "+46070717652"})
        (follow-redirect)
        (visit "/registration/564610/validate" :request-method :post :params {:code-email "METALLICA" :code-sms "345345"})
        (has (status? 422))
        (visit "/user")
        (has (status? 403)))))

(deftest registration-auto-id
  (let [participant-id (reg-service/generate-participant-id 564610 "test-" 4)]
    (with-redefs [captcha/captcha!                    (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params     (constantly {:fields                 #{:email :sms-number}
                                                                   :group                  564616
                                                                   :allow-duplicate-email? true
                                                                   :allow-duplicate-sms?   true
                                                                   :sms-countries          ["se" "gb" "dk" "no" "fi"]
                                                                   :auto-username          :participant-id
                                                                   :auto-id-prefix         "xxx-"
                                                                   :auto-id-length         3
                                                                   :auto-id?               true})
                  reg-service/generate-participant-id (constantly participant-id)
                  auth-service/letters-digits         (constantly "METALLICA")]
      (-> (session (app))
          (visit "/registration/564610")
          ;; Captcha session is created
          (follow-redirect)
          ;; Redirected do captcha page
          (follow-redirect)
          (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/registration/564610" :request-method :post :params {:email "brjann@gmail.com" :sms-number "+46070717652"})
          (follow-redirect)
          (visit "/registration/564610/validate" :request-method :post :params {:code-email "METALLICA" :code-sms "METALLICA"}))
      (let [by-username       (db/get-user-by-username {:username participant-id})
            by-participant-id (db/get-user-by-participant-id {:participant-id participant-id})]
        (is (= true (map? by-username)))
        (is (= 1 (count by-participant-id)))))))

(deftest registration-auto-id-email-username-own-password
  (let [participant-id (reg-service/generate-participant-id 564610 "test-" 4)
        email          (apply str (take 20 (repeatedly #(char (+ (rand 26) 65)))))]
    (with-redefs [captcha/captcha!                    (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params     (constantly {:fields                 #{:email :sms-number :password}
                                                                   :group                  564616
                                                                   :allow-duplicate-email? false
                                                                   :allow-duplicate-sms?   true
                                                                   :sms-countries          ["se" "gb" "dk" "no" "fi"]
                                                                   :auto-username          :email
                                                                   :auto-id-prefix         "xxx-"
                                                                   :auto-id-length         3
                                                                   :auto-id?               true})
                  reg-service/generate-participant-id (constantly participant-id)
                  auth-service/letters-digits         (constantly "METALLICA")]
      (-> (session (app))
          (visit "/registration/564610")
          ;; Captcha session is created
          (follow-redirect)
          ;; Redirected do captcha page
          (follow-redirect)
          (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/registration/564610" :request-method :post :params {:email email :sms-number "+46070717652" :password "LEMMY"})
          (follow-redirect)
          (visit "/registration/564610/validate" :request-method :post :params {:code-email "METALLICA" :code-sms "METALLICA"})
          (follow-redirect)
          (has (some-text? email))
          (has (some-text? "chose")))
      (let [by-username       (db/get-user-by-username {:username email})
            by-participant-id (db/get-user-by-participant-id {:participant-id participant-id})]
        (is (= true (map? by-username)))
        (is (= 1 (count by-participant-id)))))))

(deftest registration-auto-id-no-prefix-0-length-password
  (let [participant-id (reg-service/generate-participant-id 564610 "" 0)
        password       (passwords/password)]
    (with-redefs [captcha/captcha!                    (constantly {:filename "xxx" :digits "6666"})
                  reg-service/registration-params     (constantly {:fields                 #{:email :sms-number}
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
                  auth-service/letters-digits         (constantly "METALLICA")
                  passwords/password                  (constantly password)]
      (-> (session (app))
          (visit "/registration/564610")
          ;; Captcha session is created
          (follow-redirect)
          ;; Redirected do captcha page
          (follow-redirect)
          (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
          (follow-redirect)
          (has (some-text? "Welcome"))
          (visit "/registration/564610" :request-method :post :params {:email "brjann@gmail.com" :sms-number "+46070717652"})
          (follow-redirect)
          (visit "/registration/564610/validate" :request-method :post :params {:code-email "METALLICA" :code-sms "METALLICA"})
          (follow-redirect)
          (has (some-text? password))
          (has (some-text? participant-id)))
      (let [by-username       (db/get-user-by-username {:username participant-id})
            by-participant-id (db/get-user-by-participant-id {:participant-id participant-id})]
        (is (= true (map? by-username)))
        (is (= 1 (count by-participant-id)))
        (is (= password (:password by-username)))))))

(deftest registration-duplicate-info
  (with-redefs [captcha/captcha!                (constantly {:filename "xxx" :digits "6666"})
                reg-service/registration-params (constantly {:fields                 #{:email :sms-number}
                                                             :group                  564616
                                                             :allow-duplicate-email? false
                                                             :allow-duplicate-sms?   false
                                                             :sms-countries          ["se" "gb" "dk" "no" "fi"]})
                auth-service/letters-digits     (constantly "METALLICA")]
    (-> (session (app))
        (visit "/registration/564610")
        ;; Captcha session is created
        (follow-redirect)
        ;; Redirected do captcha page
        (follow-redirect)
        (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
        (follow-redirect)
        (has (some-text? "Welcome"))
        (visit "/registration/564610" :request-method :post :params {:email "brjann@gmail.com" :sms-number "+46070717652"})
        (follow-redirect)
        (visit "/registration/564610/validate" :request-method :post :params {:code-email "METALLICA" :code-sms "METALLICA"})
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "already exists")))))



(deftest registration-captcha-not-created
  (with-redefs [captcha/captcha! (constantly {:filename "xxx" :digits "6666"})]
    (let [response (-> (session (app))
                       (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"}))]
      (is (string/includes? (get-in response [:response :headers "Location"]) "/registration/564610/captcha")))))