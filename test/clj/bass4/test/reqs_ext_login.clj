(ns bass4.test.reqs-ext-login
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures not-text? log-return]]
            [bass4.services.auth :as auth-service]
            [bass4.db.core :as db]
            [bass4.services.user :as user]
            [clj-time.core :as t]
            [clojure.tools.logging :as log]
            [clojure.string :as string]))

(use-fixtures
  :once
  test-fixtures)

(deftest request-ext-login-not-allowed
  (with-redefs [db/ext-login-settings (constantly {:allowed false :ips ""})]
    (-> (session (app))
        (visit "/ext-login/check-pending/900")
        (has (some-text? "0 External login not allowed")))))

(deftest request-ext-login-allowed-wrong-ip
  (with-redefs [db/ext-login-settings (constantly {:allowed true :ips ""})]
    (-> (session (app))
        (visit "/ext-login/check-pending/900")
        (has (some-text? "0 External login not allowed from this IP")))))

(deftest request-ext-login-allowed-ok-ip-no-user
  (with-redefs [db/ext-login-settings (constantly {:allowed true :ips "localhost"})]
    (-> (session (app))
        (visit "/ext-login/check-pending/ext-login-x")
        (has (some-text? "0 No such user")))))

(deftest request-ext-login-allowed-ok-ip-double
  (with-redefs [db/ext-login-settings (constantly {:allowed true :ips "localhost"})]
    (-> (session (app))
        (visit "/ext-login/check-pending/ext-login-double")
        (has (some-text? "0 More than 1 matching user")))))

(deftest request-ext-login-allowed-ok-no-pending
  (with-redefs [db/ext-login-settings (constantly {:allowed true :ips "localhost"})]
    (-> (session (app))
        (visit "/ext-login/check-pending/ext-login-1")
        (has (some-text? "0 No pending administrations")))))

(deftest request-ext-login-assessment-pending
  (with-redefs [db/ext-login-settings (constantly {:allowed true :ips "localhost"})]
    (let [user-id (user/create-user! 536103 {:Group "537404" :firstname "ext-login-test"})]
      (user/update-user-properties! user-id {:username user-id :password user-id :participantid user-id})
      (let [session (session (app))
            uri     (-> session
                        (visit (str "/ext-login/check-pending/" user-id))
                        (get-in [:response :body])
                        (clojure.string/split #" ")
                        (second)
                        (string/split #"localhost")
                        (second))]
        (-> session
            (visit uri)
            (has (status? 302))
            (follow-redirect)
            (has (some-text? "Welcome"))
            (has (some-text? "top top welcome"))
            (visit "/user/")
            (has (some-text? "HAD")))))))