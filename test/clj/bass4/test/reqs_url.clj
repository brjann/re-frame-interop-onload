(ns bass4.test.reqs-url
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures]]
            [bass4.services.bass :as bass]))

(use-fixtures
  :once
  test-fixtures)

(deftest request-url-encode
  (-> (session (app))
      (visit "/debug/encode")
      (follow-redirect)
      (follow-redirect)
      (has (some-text? "{:arg1 \"val1\", :arg2 \"val2\", :arg3 \"path/to/resource\"}"))))

(deftest request-no-cache-asset
  (is (not= "no-cache, no-store, must-revalidate"
         (-> (session (app))
                (visit "/assets/bootstrap/css/bootstrap.min.css")
                (get-in [:response :headers "Cache-Control"])))))

(deftest request-no-cache-css
  (is (not= "no-cache, no-store, must-revalidate"
        (-> (session (app))
            (visit "/css/bass.css")
            (get-in [:response :headers "Cache-Control"])))))

(deftest request-no-cache-js
  (is (not= "no-cache, no-store, must-revalidate"
         (-> (session (app))
             (visit "/js/jquery-ui-slider/jquery-ui.css")
             (get-in [:response :headers "Cache-Control"])))))

(deftest request-cache-login
  (is (= "no-cache, no-store, must-revalidate"
        (-> (session (app))
                (visit "/login")
                (get-in [:response :headers "Cache-Control"])))))

(deftest request-frame-options-login
  (is (= "SAMEORIGIN"
         (-> (session (app))
             (visit "/login")
             (get-in [:response :headers "X-Frame-Options"])))))

;; Same origin test for embedded iframes. Abandoned.
;(deftest request-frame-options-embedded
;  (is (= nil
;         (with-redefs [bass/admin-session-file (constantly 110)]
;           (-> (session (app))
;               (visit "/embedded/create-session?uid=8&redirect=https://www.dn.se")
;               (visit "/embedded/instrument/1647")
;               (has (status? 200))
;               (has (some-text? "PDSR"))
;               (get-in [:response :headers "X-Frame-Options"]))))))