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
                                     log-headers
                                     log-session
                                     disable-attack-detector
                                     fix-time
                                     advance-time-s!
                                     *s*
                                     modify-session
                                     poll-message-chan
                                     messages-are?
                                     pass-by]]
            [bass4.services.auth :as auth-service]
            [bass4.middleware.debug :as debug]
            [clojure.core.async :refer [chan]]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [bass4.services.attack-detector :as a-d]
            [bass4.services.user :as user-service]
            [bass4.external-messages :as external-messages :refer [*debug-chan*]]
            [bass4.config :as config]))


(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(use-fixtures
  :each
  (fn [f]
    (binding [*debug-chan* (chan 2)]
      (f))))

(deftest double-auth-generator
  []
  (with-redefs [auth-service/double-auth-code (constantly "666777")]
    (-> *s*
        (visit "/login" :request-method :post :params {:username 536975 :password 536975})
        (has (status? 302))
        (follow-redirect)
        ;; 2 redirects because assessments middleware redirects to assessments even though double auth should be done
        (has (some-text? "666777")))))

(deftest double-auth-required
  (is (false? (auth-service/double-auth-required? 666)))
  (is (false? (auth-service/double-auth-required? 536821)))
  (is (auth-service/double-auth-required? 535759))
  (is (false? (auth-service/double-auth-required? 536834)))
  (is (auth-service/double-auth-required? 536835)))

(deftest user-exists
  (is (not= nil (user-service/get-user 536834))))

(deftest user-not-exists
  (is (nil? (user-service/get-user 666))))

(deftest wrap-identity-exists
  (-> *s*
      (modify-session {:user-id 536834})
      (visit "/debug/request")
      (has (some-text? ":user-id"))
      (has (some-text? "skipper@gmail.com"))))

(deftest wrap-identity-not-exists
  (is (= false (-> *s*
                   (modify-session {:user-id 666})
                   (visit "/debug/session")
                   (get-in [:response :body])
                   (.contains ":user-id")))))

(deftest double-auth-sms-sent
  (with-redefs [auth-service/double-auth-code (constantly "666777")]
    (-> *s*
        (visit "/login" :request-method :post :params {:username 536975 :password 536975})
        (pass-by (messages-are? [[:sms "666777"]] (poll-message-chan *debug-chan*))))))

(deftest double-auth-to-email
  (with-redefs [auth-service/double-auth-code (constantly "777666")]
    (-> *s*
        (visit "/login" :request-method :post :params {:username "to-email" :password "to-email"})
        (pass-by (messages-are? [[:email "777666"]] (poll-message-chan *debug-chan*))))))

(deftest double-auth-no-method
  (-> *s*
      (visit "/login" :request-method :post :params {:username "no-method" :password "no-method"})
      (has (status? 422))
      (has (some-text? "message"))))

#_(deftest double-auth-send-fail
    (with-redefs [debug/new-sms-in-header!  (constantly false)
                  debug/new-mail-in-header! (constantly false)]
    (-> *s*
        (visit "/login" :request-method :post :params {:username "send-fail" :password "send-fail"})
        (follow-redirect)
        (follow-redirect)
        #_(follow-redirect)
        (has (some-text? "activities")))))

(deftest double-auth-sms-priority
  (with-redefs [auth-service/double-auth-code (constantly "777666")]
    (-> *s*
        (visit "/login" :request-method :post :params {:username "to-mail-fallback" :password "to-mail-fallback"})
        (pass-by (messages-are? [[:sms "777666"]] (poll-message-chan *debug-chan*))))))

#_(deftest double-auth-mail-fallback
    (with-redefs [debug/new-sms-in-header!    (constantly false)
                auth-service/double-auth-code (constantly "777666")]
      (-> *s*
          (visit "/login" :request-method :post :params {:username "to-mail-fallback" :password "to-mail-fallback"})
          (pass-by (is (= #{{:type :email :message "777666"}} (poll-message-chan *debug-chan*)))))))

(deftest request-double-authentication
  (-> *s*
      (modify-session {:user-id 536975 :double-auth-code "666-666-666"})
      (visit "/user/tx/messages")
      (has (status? 302))
      (follow-redirect)
      (has (some-text? "666-666-666"))
      (visit "/double-auth")
      (has (some-text? "666-666-666"))
      (modify-session {:double-authed? true})
      (visit "/user/tx/messages")
      (has (status? 200))))

(deftest request-double-authentication-no-re-auth
  (-> *s*
      (modify-session {:user-id 536975 :double-auth-code "666-666-666" :external-login? true})
      (visit "/user")
      (visit "/user/tx/messages")
      (has (status? 200))))

(deftest request-no-double-authentication
  (-> *s*
      (modify-session {:user-id 536821})
      (visit "/user/")
      (follow-redirect)
      (has (some-text? "activities"))))

(deftest request-no-double-authentication-visit
  (is (= true (-> *s*
                  (modify-session {:user-id 536821})
                  (visit "/double-auth")
                  (get-in [:response :headers "Location"])
                  (.contains "/user/")))))

(deftest request-not-user-double-authentication-visit
  (is (= true (-> *s*
                  (modify-session {:user-id 666})
                  (visit "/double-auth")
                  (get-in [:response :headers "Location"])
                  (.contains "/login")))))

(deftest request-403
  (-> *s*
      (visit "/user/tx/messages")
      (has (status? 403))))

(deftest request-re-auth
  (-> *s*
      (modify-session {:user-id 536975 :double-authed? true})
      (visit "/user")
      (visit "/user/tx/messages")
      (has (status? 200))
      (modify-session {:auth-re-auth? true})
      (visit "/user/tx/messages")
      (has (status? 302))
      (follow-redirect)
      (has (some-text? "Authenticate again"))))

(deftest request-re-auth-ext-login
  (-> *s*
      (modify-session {:user-id 536975 :double-authed? true})
      (visit "/user")
      (visit "/user/tx/messages")
      (has (status? 200))
      (modify-session {:auth-re-auth? true :external-login? true})
      (visit "/user/tx/messages")
      (has (status? 200))))

(deftest request-re-auth-pwd
  (-> *s*
      (modify-session {:user-id 536975 :double-authed? true :auth-re-auth? true})
      (visit "/re-auth" :request-method :post :params {:password 53589})
      (has (status? 422))
      (visit "/re-auth" :request-method :post :params {:password 536975})
      (has (status? 302))))

(deftest request-re-auth-pwd-redirect
  (is (= true (-> *s*
                  (modify-session {:user-id 536975 :double-authed? true :auth-re-auth? true})
                  (visit "/re-auth" :request-method :post :params {:password 536975 :return-url "/user/tx/messages"})
                  (get-in [:response :headers "Location"])
                  (.contains "/user/tx/messages")))))

(deftest request-re-auth-pwd-ajax
  (-> *s*
      (modify-session {:user-id 536975 :double-authed? true :auth-re-auth? true})
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
      (modify-session {:user-id 536975 :double-authed? true})
      (visit "/re-auth" :request-method :post :params {:password 23254})
      (has (status? 302))))

(deftest request-re-auth-pwd-unnecessary-right
  "User is not timed out and it should not matter what password is sent"
  (-> *s*
      (modify-session {:user-id 536975 :double-authed? true})
      (visit "/re-auth" :request-method :post :params {:password 536975})
      (has (status? 302))))

(deftest modify-session-test
  (-> *s*
      (modify-session {:test888 "hejsan"})
      (visit "/debug/session")
      (has (some-text? ":test888"))))

(deftest request-re-auth-timeout
  (fix-time
    (-> *s*
        (modify-session {:user-id 536975 :double-authed? true})
        (visit "/user")
        (visit "/user/tx/messages")
        (has (status? 200))
        (advance-time-s! (dec (config/env :timeout-soft)))
        (visit "/user/tx/messages")
        (has (status? 200))
        (advance-time-s! (config/env :timeout-soft))
        (visit "/user/tx/messages")
        (has (status? 302))
        (visit "/user/tx/messages")
        (has (status? 302))
        (visit "/re-auth" :request-method :post :params {:password 536975})
        (has (status? 302))
        (visit "/user/tx/messages")
        (has (status? 200)))))

(deftest request-re-auth-timeout-external-login
  (fix-time
    (-> *s*
        (modify-session {:user-id 536975 :double-authed? true :external-login? false})
        (visit "/user")
        (visit "/user/tx/messages")
        (has (status? 200))
        (advance-time-s! (config/env :timeout-soft))
        (visit "/user/tx/messages")
        (has (status? 302))))
  (fix-time
    (-> *s*
        (modify-session {:user-id 536975 :double-authed? true :external-login? true})
        (visit "/user")
        (visit "/user/tx/messages")
        (has (status? 200))
        (advance-time-s! (config/env :timeout-soft))
        (visit "/user/tx/messages")
        (has (status? 200)))))

(deftest request-re-auth-timeout-re-auth
  (fix-time
    (-> *s*
        (modify-session {:user-id 536975 :double-authed? true})
        (visit "/user/tx/messages")
        (has (status? 200))
        (advance-time-s! (config/env :timeout-soft))
        (visit "/user/tx/messages")
        (follow-redirect)
        (visit "/re-auth" :request-method :post :params {:password 536975})
        (has (status? 302))
        (visit "/user")
        (visit "/user/tx/messages")
        (has (status? 200)))))

;; Don't understand what this test was testing
#_(deftest request-re-auth-last-request-time3
  (-> *s*
      (modify-session {:user-id 536975 :double-authed? true})
      (modify-session {:last-request-time (t/date-time 1985 10 26 1 20 0 0)})
      (visit "/debug/session")
      (has (some-text? "1985-10-26T01:20:00.000Z"))
      (visit "/user/tx/messages")
      (has (status? 302))
      (modify-session {:last-request-time (t/date-time 1985 10 26 1 20 0 0)})
      (visit "/debug/session")
      (has (some-text? "1985-10-26T01:20:00.000Z"))
      (visit "/re-auth" :request-method :post :params {:password 536975})
      (follow-redirect)
      (visit "/user")
      (visit "/user/tx/messages")
      (has (status? 200))))

(deftest request-re-auth-ajax
  (-> *s*
      (modify-session {:user-id 536975 :double-authed? true :auth-re-auth? true})
      (visit "/user/tx/messages")
      (has (status? 302))
      (visit "/user/tx/messages" :request-method :post :params {:text "xxx"})
      (has (status? 302))
      (visit "/user/tx/messages" :request-method :post :headers {"x-requested-with" "XMLHttpRequest"} :params {:text "xxx"})
      (has (status? 440))))