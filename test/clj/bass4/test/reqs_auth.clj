(ns bass4.test.reqs-auth
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures debug-headers-text?]]
            [bass4.services.auth :as auth-service]
            [bass4.services.user :as user]
            [bass4.middleware.debug-redefs :as debug]
            [clojure.tools.logging :as log]))




(deftest double-auth-generator
  []
  (with-redefs [auth-service/double-auth-code (constantly "666777")]
    (-> (session (app))
        (visit "/login" :request-method :post :params {:username 536975 :password 536975})
        (has (status? 302))
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
  (-> (session (app))
      (visit "/debug/set-session" :params {:identity 536834})
      (visit "/debug/session")
      (has (some-text? "identity"))
      (has (some-text? "skipper@gmail.com"))))

(deftest wrap-identity-not-exists
  (is (= false (-> (session (app))
                   (visit "/debug/set-session" :params {:identity 666})
                   (visit "/debug/session")
                   (get-in [:response :body])
                   (.contains ":identity")))))

(deftest double-auth-sms-sent
  (with-redefs [auth-service/double-auth-code (constantly "666777")]
    (-> (session (app))
        (visit "/login" :request-method :post :params {:username 536975 :password 536975})
        (debug-headers-text? "SMS" "666777"))))

(deftest double-auth-to-email
  (with-redefs [auth-service/double-auth-code (constantly "777666")]
    (-> (session (app))
        (visit "/login" :request-method :post :params {:username "to-email" :password "to-email"})
        (debug-headers-text? "MAIL" "777666"))))

(deftest double-auth-no-method
  (-> (session (app))
      (visit "/login" :request-method :post :params {:username "no-method" :password "no-method"})
      (follow-redirect)
      (has (status? 404))))

(deftest double-auth-send-fail
  (with-redefs [debug/test-send-sms! (constantly false)
                debug/test-mail! (constantly false)]
    (-> (session (app))
        (visit "/login" :request-method :post :params {:username "send-fail" :password "send-fail"})
        (follow-redirect)
        (has (some-text? "dashboard")))))

(deftest double-auth-sms-priority
  (with-redefs [auth-service/double-auth-code (constantly "777666")]
    (-> (session (app))
        (visit "/login" :request-method :post :params {:username "to-mail-fallback" :password "to-mail-fallback"})
        (debug-headers-text? "SMS" "777666"))))

(deftest double-auth-mail-fallback
  (with-redefs [debug/test-send-sms! (constantly false)
                auth-service/double-auth-code (constantly "777666")]
    (-> (session (app))
        (visit "/login" :request-method :post :params {:username "to-mail-fallback" :password "to-mail-fallback"})
        (debug-headers-text? "MAIL" "777666"))))

(deftest request-double-authentication
  (-> (session (app))
      (visit "/debug/set-session" :params {:identity 536975 :double-auth-code "666-666-666"})
      (visit "/user/messages")
      (has (status? 302))
      (follow-redirect)
      (has (some-text? "666-666-666"))
      (visit "/double-auth")
      (has (some-text? "666-666-666"))
      (visit "/debug/set-session" :params {:double-authed 1})
      (visit "/user/messages")
      (has (status? 200))))

(deftest request-no-double-authentication
  (-> (session (app))
      (visit "/debug/set-session" :params {:identity 536821})
      (visit "/user/messages")
      (has (status? 200))))

(deftest request-no-double-authentication-visit
  (is (= true (-> (session (app))
                   (visit "/debug/set-session" :params {:identity 536821})
                   (visit "/double-auth")
                   (get-in [:response :headers "Location"])
                   (.contains "/user/")))))

(deftest request-not-user-double-authentication-visit
  (is (= true (-> (session (app))
                  (visit "/debug/set-session" :params {:identity 666})
                  (visit "/double-auth")
                  (get-in [:response :headers "Location"])
                  (.contains "/login")))))

(deftest request-403
  (-> (session (app))
      (visit "/user/messages")
      (has (status? 403))))

(deftest request-re-auth
  (-> (session (app))
      (visit "/debug/set-session" :params {:identity 536975 :double-authed 1})
      (visit "/user/messages")
      (has (status? 200))
      (visit "/debug/set-session" :params {:auth-timeout true})
      (visit "/user/messages")
      (has (status? 302))
      (follow-redirect)
      (has (some-text? "Authenticate again"))))

(deftest request-re-auth-pwd
  (-> (session (app))
      (visit "/debug/set-session" :params {:identity 536975 :double-authed 1 :auth-timeout true})
      (visit "/re-auth" :request-method :post :params {:password 53589})
      (has (status? 422))
      (visit "/re-auth" :request-method :post :params {:password 536975})
      (has (status? 302))))

(deftest request-re-auth-pwd-redirect
  (is (= true (-> (session (app))
                  (visit "/debug/set-session" :params {:identity 536975 :double-authed 1 :auth-timeout true})
                  (visit "/re-auth" :request-method :post :params {:password 536975 :return-url "/user/messages"})
                  (get-in [:response :headers "Location"])
                  (.contains "/user/messages")))))

(deftest request-re-auth-pwd-ajax
  (-> (session (app))
      (visit "/debug/set-session" :params {:identity 536975 :double-authed 1 :auth-timeout true})
      (visit "/re-auth-ajax" :request-method :post :params {:password 53589})
      (has (status? 422))
      (visit "/re-auth-ajax" :request-method :post :params {:password 536975})
      (has (status? 200))))

(deftest request-re-auth-pwd-unauthorized
  (-> (session (app))
      (visit "/re-auth" :request-method :post :params {:password 536975})
      (has (status? 403))))

(deftest request-re-auth-pwd-ajax-unauthorized
  (-> (session (app))
      (visit "/re-auth-ajax" :request-method :post :params {:password 536975})
      (has (status? 403))))

(deftest request-re-auth-pwd-unnecessary-wrong
  "User is not timed out and it should not matter what password is sent"
  (-> (session (app))
      (visit "/debug/set-session" :params {:identity 536975 :double-authed 1})
      (visit "/re-auth" :request-method :post :params {:password 23254})
      (has (status? 302))))

(deftest request-re-auth-pwd-unnecessary-right
  "User is not timed out and it should not matter what password is sent"
  (-> (session (app))
      (visit "/debug/set-session" :params {:identity 536975 :double-authed 1})
      (visit "/re-auth" :request-method :post :params {:password 536975})
      (has (status? 302))))