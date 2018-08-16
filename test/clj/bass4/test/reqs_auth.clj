(ns bass4.test.reqs-auth
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures
                                     debug-headers-text?
                                     log-return
                                     log-body
                                     log-status
                                     disable-attack-detector
                                     *s*
                                     modify-session]]
            [bass4.services.auth :as auth-service]
            [bass4.services.user :as user]
            [bass4.middleware.debug :as debug]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [bass4.services.attack-detector :as a-d]))


(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(deftest double-auth-generator
  []
  (with-redefs [auth-service/double-auth-code (constantly "666777")]
    (-> *s*
        (visit "/login" :request-method :post :params {:username 536975 :password 536975})
        (has (status? 302))
        (follow-redirect)
        ;; 2 redirects because assessments middleware redirects to assessments even though double auth should be done
        (follow-redirect)
        (follow-redirect)
        (has (some-text? "666777")))))

(deftest double-auth-required
  (is (false? (auth-service/double-auth-required? 666)))
  (is (false? (auth-service/double-auth-required? 536821)))
  (is (auth-service/double-auth-required? 535759))
  (is (false? (auth-service/double-auth-required? 536834)))
  (is (auth-service/double-auth-required? 536835)))

(deftest user-exists
  (is (not= nil (user/get-user 536834))))

(deftest user-not-exists
  (is (nil? (user/get-user 666))))

(deftest wrap-identity-exists
  (-> *s*
      (modify-session {:identity 536834})
      (visit "/debug/session")
      (has (some-text? "identity"))
      (has (some-text? "skipper@gmail.com"))))

(deftest wrap-identity-not-exists
  (is (= false (-> *s*
                   (modify-session {:identity 666})
                   (visit "/debug/session")
                   (get-in [:response :body])
                   (.contains ":identity")))))

(deftest double-auth-sms-sent
  (with-redefs [auth-service/double-auth-code (constantly "666777")]
    (-> *s*
        (visit "/login" :request-method :post :params {:username 536975 :password 536975})
        (debug-headers-text? "SMS" "666777"))))

(deftest double-auth-to-email
  (with-redefs [auth-service/double-auth-code (constantly "777666")]
    (-> *s*
        (visit "/login" :request-method :post :params {:username "to-email" :password "to-email"})
        (debug-headers-text? "MAIL" "777666"))))

(deftest double-auth-no-method
  (-> *s*
      (visit "/login" :request-method :post :params {:username "no-method" :password "no-method"})
      (has (status? 422))
      (has (some-text? "message"))))

(deftest double-auth-send-fail
  (with-redefs [debug/sms-in-header!  (constantly false)
                debug/mail-in-header! (constantly false)]
    (-> *s*
        (visit "/login" :request-method :post :params {:username "send-fail" :password "send-fail"})
        (follow-redirect)
        (follow-redirect)
        (follow-redirect)
        (log-body)
        (has (some-text? "activities")))))

(deftest double-auth-sms-priority
  (with-redefs [auth-service/double-auth-code (constantly "777666")]
    (-> *s*
        (visit "/login" :request-method :post :params {:username "to-mail-fallback" :password "to-mail-fallback"})
        (debug-headers-text? "SMS" "777666"))))

(deftest double-auth-mail-fallback
  (with-redefs [debug/sms-in-header!          (constantly false)
                auth-service/double-auth-code (constantly "777666")]
    (-> *s*
        (visit "/login" :request-method :post :params {:username "to-mail-fallback" :password "to-mail-fallback"})
        (debug-headers-text? "MAIL" "777666"))))

(deftest request-double-authentication
  (-> *s*
      (modify-session {:identity 536975 :double-auth-code "666-666-666"})
      (visit "/user/messages")
      (has (status? 302))
      (follow-redirect)
      (has (some-text? "666-666-666"))
      (visit "/double-auth")
      (has (some-text? "666-666-666"))
      (modify-session {:double-authed? true})
      (visit "/user/messages")
      (has (status? 200))))

(deftest request-double-authentication-no-re-auth
  (-> *s*
      (modify-session {:identity 536975 :double-auth-code "666-666-666" :external-login? true})
      (visit "/user")
      (visit "/user/messages")
      (has (status? 200))))

(deftest request-no-double-authentication
  (-> *s*
      (modify-session {:identity 536821})
      (visit "/user/")
      (follow-redirect)
      (has (some-text? "activities"))))

(deftest request-no-double-authentication-visit
  (is (= true (-> *s*
                  (modify-session {:identity 536821})
                  (visit "/double-auth")
                  (get-in [:response :headers "Location"])
                  (.contains "/user/")))))

(deftest request-not-user-double-authentication-visit
  (is (= true (-> *s*
                  (modify-session {:identity 666})
                  (visit "/double-auth")
                  (get-in [:response :headers "Location"])
                  (.contains "/login")))))

(deftest request-403
  (-> *s*
      (visit "/user/messages")
      (has (status? 403))))

(deftest request-re-auth
  (-> *s*
      (modify-session {:identity 536975 :double-authed? true})
      (visit "/user")
      (visit "/user/messages")
      (has (status? 200))
      (modify-session {:auth-re-auth true})
      (visit "/user/messages")
      (has (status? 302))
      (follow-redirect)
      (has (some-text? "Authenticate again"))))

(deftest request-re-auth-ext-login
  (-> *s*
      (modify-session {:identity 536975 :double-authed? true})
      (visit "/user")
      (visit "/user/messages")
      (has (status? 200))
      (modify-session {:auth-re-auth true :external-login? true})
      (visit "/user/messages")
      (has (status? 200))))

(deftest request-re-auth-pwd
  (-> *s*
      (modify-session {:identity 536975 :double-authed? true :auth-re-auth true})
      (visit "/re-auth" :request-method :post :params {:password 53589})
      (has (status? 422))
      (visit "/re-auth" :request-method :post :params {:password 536975})
      (has (status? 302))))

(deftest request-re-auth-pwd-redirect
  (is (= true (-> *s*
                  (modify-session {:identity 536975 :double-authed? true :auth-re-auth true})
                  (visit "/re-auth" :request-method :post :params {:password 536975 :return-url "/user/messages"})
                  (get-in [:response :headers "Location"])
                  (.contains "/user/messages")))))

(deftest request-re-auth-pwd-ajax
  (-> *s*
      (modify-session {:identity 536975 :double-authed? true :auth-re-auth true})
      (visit "/re-auth-ajax" :request-method :post :params {:password 53589})
      (has (status? 422))
      (visit "/re-auth-ajax" :request-method :post :params {:password 536975})
      (has (status? 200))))

(deftest request-re-auth-pwd-unauthorized
  (-> *s*
      (visit "/re-auth" :request-method :post :params {:password 536975})
      (has (status? 403))))

(deftest request-re-auth-pwd-ajax-unauthorized
  (-> *s*
      (visit "/re-auth-ajax" :request-method :post :params {:password 536975})
      (has (status? 403))))

(deftest request-re-auth-pwd-unnecessary-wrong
  "User is not timed out and it should not matter what password is sent"
  (-> *s*
      (modify-session {:identity 536975 :double-authed? true})
      (visit "/re-auth" :request-method :post :params {:password 23254})
      (has (status? 302))))

(deftest request-re-auth-pwd-unnecessary-right
  "User is not timed out and it should not matter what password is sent"
  (-> *s*
      (modify-session {:identity 536975 :double-authed? true})
      (visit "/re-auth" :request-method :post :params {:password 536975})
      (has (status? 302))))

(deftest modify-session-test
  (let [x *s*]
    (-> (binding [debug/*session-modification* {:test888 "hejsan"}]
          (-> x
              (visit "/debug/session")
              (has (some-text? ":test888"))))
        (visit "/debug/session")
        (has (some-text? ":test888")))))

(deftest request-re-auth-last-request-time
  (let [x (-> *s*
              (modify-session {:identity 536975 :double-authed? true})
              (visit "/user")
              (visit "/user/messages")
              (has (status? 200))
              (visit "/debug/session"))]
    (-> (binding [debug/*session-modification* {:last-request-time (t/date-time 1985 10 26 1 20 0 0)}]
          (-> x
              (visit "/debug/session")
              (has (some-text? "1985-10-26T01:20:00.000Z"))))
        (visit "/user/messages")
        (has (status? 302))
        (visit "/user/messages")
        (has (status? 302))
        (visit "/re-auth" :request-method :post :params {:password 536975})
        (has (status? 302))
        (visit "/user/messages")
        (has (status? 200)))))

(deftest request-re-auth-last-request-time-no-re-auth
  (let [x (-> *s*
              (modify-session {:identity 536975 :double-authed? true :external-login? true})
              (visit "/user")
              (log-body)
              (follow-redirect)
              (visit "/user/messages")
              (has (status? 200))
              (visit "/debug/session"))]
    (-> (binding [debug/*session-modification* {:last-request-time (t/date-time 1985 10 26 1 20 0 0)}]
          (-> x
              (visit "/debug/session")
              (has (some-text? "1985-10-26T01:20:00.000Z"))))
        (visit "/user/messages")
        (has (status? 200)))))

(deftest request-re-auth-last-request-time2
  (let [x (-> *s*
              (modify-session {:identity 536975 :double-authed? true}))]
    ;; TODO: Rewrite session modification as macro
    (-> (binding [debug/*session-modification* {:last-request-time (t/date-time 1985 10 26 1 20 0 0)}]
          (-> x
              (visit "/debug/session")
              (has (some-text? "1985-10-26T01:20:00.000Z"))))
        (visit "/user/messages")
        (visit "/re-auth" :request-method :post :params {:password 536975})
        (has (status? 302))
        (visit "/user")
        (visit "/user/messages")
        (has (status? 200)))))


(deftest request-re-auth-last-request-time3
  (let [x (-> *s*
              (modify-session {:identity 536975 :double-authed? true}))
        y (-> (binding [debug/*session-modification* {:last-request-time (t/date-time 1985 10 26 1 20 0 0)}]
                (-> x
                    (visit "/debug/session")
                    (has (some-text? "1985-10-26T01:20:00.000Z"))))
              (visit "/user/messages"))]
    (-> (binding [debug/*session-modification* {:last-request-time (t/date-time 1985 10 26 1 20 0 0)}]
          (-> y
              (visit "/debug/session")
              (has (some-text? "1985-10-26T01:20:00.000Z"))))
        (visit "/re-auth" :request-method :post :params {:password 536975})
        (visit "/user")
        (visit "/user/messages")
        (has (status? 200)))))