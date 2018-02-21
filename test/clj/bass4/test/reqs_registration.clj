(ns bass4.test.reqs-registration
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures debug-headers-text? log-return]]
            [bass4.captcha :as captcha]
            [bass4.services.auth :as auth-service]
            [bass4.services.user :as user]
            [bass4.middleware.debug :as debug]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [clojure.string :as string]))


(use-fixtures
  :once
  test-fixtures)


(deftest registration-not-allowed
  (-> (session (app))
      (visit "/registration/564612")
      (has (some-text? "Registration not allowed"))))

(deftest registration-captcha
  (with-redefs [captcha/captcha! (constantly {:filename "xxx" :digits "6666"})]
    (-> (session (app))
        (visit "/registration/564610")
        ;; Captcha session is created
        (follow-redirect)
        ;; Redirected do captcha page
        (follow-redirect)
        (has (some-text? "image"))
        (visit "/registration/564610/captcha" :request-method :post :params {:captcha "234234"})
        (has (status? 422))
        (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"})
        (has (status? 302))
        (visit "/registration/564610/captcha")
        (has (status? 302)))))

(deftest registration-captcha-not-created
  (with-redefs [captcha/captcha! (constantly {:filename "xxx" :digits "6666"})]
    (let [response (-> (session (app))
                       (visit "/registration/564610/captcha" :request-method :post :params {:captcha "6666"}))]
      (is (string/includes? (get-in response [:response :headers "Location"]) "/registration/564610/captcha")))))