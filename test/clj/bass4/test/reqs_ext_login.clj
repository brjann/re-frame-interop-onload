(ns ^:eftest/synchronized
  ;; redefs env and I'm not sure if it's worth it to refactor that into a
  ;; rebindable function.
  bass4.test.reqs-ext-login
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.routes.ext-login :as ext-login]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer :all]
            [bass4.test.assessment-utils :refer :all]
            [bass4.db.core :as db]
            [bass4.services.user :as user-service]
            [clj-time.core :as t]
            [clojure.string :as string]
            [bass4.config :as config]
            [clojure.tools.logging :as log]))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(use-fixtures
  :each
  random-date-tz-fixture)
(deftest request-ext-login-not-allowed
  (binding [ext-login/db-ext-login-settings (constantly {:allowed? false :ips ""})]
    (-> *s*
        (visit "/ext-login/check-pending/900")
        (has (some-text? "0 External login not allowed")))))

(deftest request-ext-login-allowed-wrong-ip
  (binding [ext-login/db-ext-login-settings (constantly {:allowed? true :ips ""})]
    (-> *s*
        (visit "/ext-login/check-pending/900")
        (has (some-text? "0 External login not allowed from this IP")))))

(deftest request-ext-login-x-forwarded-for
  (binding [ext-login/db-ext-login-settings (constantly {:allowed? true :ips "127.0.0.1"})]
    (let [user-id (user-service/create-user! project-double-auth)]
      (user-service/update-user-properties! user-id {"participantid" user-id})
      (with-redefs [config/env (merge config/env {:x-forwarded-for-index 0})]
        (-> *s*
            (visit (str "/ext-login/check-pending/" user-id) :headers {"x-forwarded-for" "127.0.0.1 255.255.255.255"})
            (has (some-text? "0 External login not allowed from this IP"))))
      (with-redefs [config/env (merge config/env {:x-forwarded-for-index 1})]
        (-> *s*
            (visit (str "/ext-login/check-pending/" user-id) :headers {"x-forwarded-for" "127.0.0.1 255.255.255.255"})
            (has (some-text? "0 No pending administrations")))))))

(deftest request-ext-login-allowed-ok-ip-double
  (binding [ext-login/db-ext-login-settings (constantly {:allowed? true :ips "127.0.0.1"})]
    (let [user-id1 (user-service/create-user! project-double-auth)
          user-id2 (user-service/create-user! project-double-auth)]
      (user-service/update-user-properties! user-id1 {"participantid" user-id1})
      (user-service/update-user-properties! user-id2 {"participantid" user-id1})
      (-> *s*
          (visit (str "/ext-login/check-pending/" user-id1))
          (has (some-text? "0 More than 1 matching user"))))))

(deftest request-ext-login-allowed-ok-ip-no-user
  (binding [ext-login/db-ext-login-settings (constantly {:allowed? true :ips "127.0.0.1"})]
    (-> *s*
        (visit "/ext-login/check-pending/METALLICA")
        (has (some-text? "0 No such user")))))

(deftest request-ext-login-allowed-ok-no-pending
  (binding [ext-login/db-ext-login-settings (constantly {:allowed? true :ips "127.0.0.1"})]
    (let [user-id (user-service/create-user! project-double-auth)]
      (user-service/update-user-properties! user-id {"participantid" user-id})
      (-> *s*
          (visit (str "/ext-login/check-pending/" user-id))
          (has (some-text? "0 No pending administrations"))))))

(deftest request-ext-login-assessment-pending
  (binding [ext-login/db-ext-login-settings (constantly {:allowed? true :ips "127.0.0.1"})]
    (let [group-id (create-assessment-group! project-double-auth project-double-auth-assessment-series [286 4431])
          user-id  (user-service/create-user! project-double-auth {"group" group-id})]
      (user-service/update-user-properties! user-id {"participantid" user-id})
      (let [session  *s*
            uri      (-> session
                         (visit (str "/ext-login/check-pending/" user-id))
                         (log-body)
                         (get-in [:response :body])
                         (string/split #"localhost")
                         (second))
            _        (log/debug uri)
            redirect (-> session
                         (visit (str uri "&returnURL=htp%3A%2F%2Fwww.dn.se"))
                         (has (status? 400))
                         (visit (str uri "&returnURL=http%3A%2Fwww.dn.se"))
                         (has (status? 400))
                         (visit (str uri "&returnURL=http%3A%2F%2Fwww.dn.se"))
                         (visit "/user")
                         (follow-redirect)
                         (visit "/user/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
                         (follow-redirect)
                         (visit "/user/assessments" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"})
                         (follow-redirect)
                         (has (some-text? "Thanks"))
                         ;; Assessments pending becomes false in the next request.
                         ;; At users first request after thank-you-text has been shown
                         (visit "/user/assessments")
                         ;; ext-login middleware catches the change in assessments-pending?
                         ;; status and redirects user
                         (has (status? 302))
                         (get-in [:response :headers "Location"]))]
        (is (= "http://www.dn.se" redirect))
        (-> session
            (visit "/user/tx/messages")
            (has (status? 403)))))))

(deftest request-ext-login-assessment-pending-logout-url
  (binding [ext-login/db-ext-login-settings (constantly {:allowed? true :ips "127.0.0.1"})]
    (let [group-id (create-assessment-group! project-double-auth project-double-auth-assessment-series [4431 286])
          user-id  (user-service/create-user! project-double-auth {:group group-id})]
      (user-service/update-user-properties! user-id {"participantid" user-id})
      (let [session  *s*
            uri      (-> session
                         (visit (str "/ext-login/check-pending/" user-id))
                         (get-in [:response :body])
                         (string/split #"localhost")
                         (second))
            redirect (-> session
                         (visit (str uri "&returnURL=http%3A%2F%2Fwww.dn.se&logoutURL=http%3A%2F%2Fwww.svd.se"))
                         (visit "/user")
                         (follow-redirect)
                         (visit "/user/assessments" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"})
                         (visit "/user/assessments")
                         (has (status? 200))
                         (visit "/logout")
                         (has (status? 302))
                         (get-in [:response :headers "Location"]))]
        (is (= "http://www.svd.se" redirect))
        (-> session
            (visit "/user/assessments")
            (has (status? 403)))))))

(deftest request-ext-login-empty-assessment-pending2
  (binding [ext-login/db-ext-login-settings (constantly {:allowed? true :ips "127.0.0.1"})]
    (let [group-id (create-assessment-group! project-double-auth project-double-auth-assessment-series [] {})
          user-id  (user-service/create-user! project-double-auth {:group group-id})]
      (user-service/update-user-properties! user-id {"participantid" user-id})
      (let [session  *s*
            uri      (-> session
                         (visit (str "/ext-login/check-pending/" user-id))
                         (get-in [:response :body])
                         (string/split #"localhost")
                         (second))
            redirect (-> session
                         (visit (str uri "&returnURL=http%3A%2F%2Fwww.dn.se"))
                         (visit "/user")
                         (has (status? 302))
                         ;; ext-login middleware catches the change in assessments-pending?
                         ;; status and redirects user
                         (get-in [:response :headers "Location"]))]
        (is (= "http://www.dn.se" redirect))))))


(deftest request-ext-login-error-uid
  (binding [ext-login/db-ext-login-settings (constantly {:allowed? true :ips "127.0.0.1"})]
    (let [session  *s*
          redirect (-> session
                       (visit "/ext-login/do-login?uid=666&returnURL=http%3A%2F%2Fwww.dn.se")
                       (has (status? 302))
                       (get-in [:response :headers "Location"]))]
      (is (= "http://www.dn.se" redirect))
      (-> session
          (visit "/user/tx/messages")
          (has (status? 403))))))