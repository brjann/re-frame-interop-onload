(ns bass4.test.reqs-ext-login
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures
                                     not-text?
                                     log-return
                                     log-body
                                     log-headers
                                     log-session
                                     disable-attack-detector
                                     *s*]]
            [bass4.services.auth :as auth-service]
            [bass4.db.core :as db]
            [bass4.services.user :as user-service]
            [clj-time.core :as t]
            [clojure.tools.logging :as log]
            [clojure.string :as string]))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(deftest request-ext-login-not-allowed
  (with-redefs [db/ext-login-settings (constantly {:allowed? false :ips ""})]
    (-> *s*
        (visit "/ext-login/check-pending/900")
        (has (some-text? "0 External login not allowed")))))

(deftest request-ext-login-allowed-wrong-ip
  (with-redefs [db/ext-login-settings (constantly {:allowed? true :ips ""})]
    (-> *s*
        (visit "/ext-login/check-pending/900")
        (has (some-text? "0 External login not allowed from this IP")))))

(deftest request-ext-login-allowed-ok-ip-no-user
  (with-redefs [db/ext-login-settings (constantly {:allowed? true :ips "localhost"})]
    (-> *s*
        (visit "/ext-login/check-pending/ext-login-x")
        (has (some-text? "0 No such user")))))

(deftest request-ext-login-allowed-ok-ip-double
  (with-redefs [db/ext-login-settings (constantly {:allowed? true :ips "localhost"})]
    (-> *s*
        (visit "/ext-login/check-pending/ext-login-double")
        (has (some-text? "0 More than 1 matching user")))))

(deftest request-ext-login-allowed-ok-no-pending
  (with-redefs [db/ext-login-settings (constantly {:allowed? true :ips "localhost"})]
    (-> *s*
        (visit "/ext-login/check-pending/ext-login-1")
        (has (some-text? "0 No pending administrations")))))

(deftest request-ext-login-assessment-pending
  (with-redefs [db/ext-login-settings (constantly {:allowed? true :ips "localhost"})]
    (let [user-id (user-service/create-user! 536103 {:Group "537404" :firstname "ext-login-test"})]
      (user-service/update-user-properties! user-id {:username user-id :password user-id :participantid user-id})
      (let [session  *s*
            uri      (-> session
                        (visit (str "/ext-login/check-pending/" user-id))
                        (get-in [:response :body])
                        (string/split #"localhost")
                        (second))
            redirect (-> session
                         (visit (str uri "&returnURL=htp://www.dn.se"))
                         (has (status? 400))
                         (visit (str uri "&returnURL=http:/www.dn.se"))
                         (has (status? 400))
                         (visit (str uri "&returnURL=http://www.dn.se"))
                         (visit "/user")
                         (follow-redirect)
                         (visit "/user/assessments" :request-method :post :params {:instrument-id 4431 :items "{}" :specifications "{}"})
                         (visit "/user/assessments" :request-method :post :params {:instrument-id 4743 :items "{}" :specifications "{}"})
                         (visit "/user/assessments" :request-method :post :params {:instrument-id 4568 :items "{}" :specifications "{}"})
                         (follow-redirect)
                         (visit "/user/assessments" :request-method :post :params {:instrument-id 286 :items "{}" :specifications "{}"})
                         (follow-redirect)
                         (has (some-text? "Thanks top-priority"))
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

(deftest request-ext-login-empty-assessment-pending
  (with-redefs [db/ext-login-settings (constantly {:allowed? true :ips "localhost"})]
    (let [user-id (user-service/create-user! 536103 {:Group "642451" :firstname "ext-login-test-empty"})]
      (user-service/update-user-properties! user-id {:username user-id :password user-id :participantid user-id})
      (let [session  *s*
            uri      (-> session
                         (visit (str "/ext-login/check-pending/" user-id))
                         (get-in [:response :body])
                         (string/split #"localhost")
                         (second))
            redirect (-> session
                         (visit (str uri "&returnURL=http://www.dn.se"))
                         (visit "/user")
                         ;; ext-login middleware catches the change in assessments-pending?
                         ;; status and redirects user
                         (get-in [:response :headers "Location"]))]
        (is (= "http://www.dn.se" redirect))))))


(deftest request-ext-login-error-uid
  (with-redefs [db/ext-login-settings (constantly {:allowed? true :ips "localhost"})]
    (let [user-id (user-service/create-user! 536103 {:Group "537404" :firstname "ext-login-test"})]
      (user-service/update-user-properties! user-id {:username user-id :password user-id :participantid user-id})
      (let [session  *s*
            redirect (-> session
                         (visit "/ext-login/do-login?uid=666&returnURL=http://www.dn.se")
                         (has (status? 302))
                         (get-in [:response :headers "Location"]))]
        (is (= "http://www.dn.se" redirect))
        (-> session
            (visit "/user/tx/messages")
            (has (status? 403)))))))