(ns bass4.test.reqs-auth
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer :all]
            [bass4.services.auth :as auth-service]
            [clojure.core.async :refer [chan]]
            [bass4.services.user :as user-service]
            [bass4.external-messages.async :as external-messages :refer [*debug-chan*]]
            [bass4.config :as config]
            [clojure.tools.logging :as log]))

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
  (binding [auth-service/double-auth-code (constantly "666777")]
    (let [user-id (create-user-with-password! {"SMSNumber" "070-7176562"})]
      ;; double auth
      (-> *s*
          (visit "/login" :request-method :post :params {:username (str user-id) :password (str user-id)})
          (has (status? 302))
          (follow-redirect)
          ;; 2 redirects because assessments middleware redirects to assessments even though double auth should be done
          (has (some-text? "666777")))
      ;; double-auth-sms-sent
      (-> *s*
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (pass-by (messages-are? [[:sms "666777"]] (poll-message-chan *debug-chan*)))))))

(deftest double-auth-required
  (is (false? (auth-service/double-auth-required? nil)))
  (is (false? (auth-service/double-auth-required?* {:sms? false :email? false})))
  (is (map? (auth-service/double-auth-required?* {:sms? true :user-skip? true :allow-skip? false})))
  (is (false? (auth-service/double-auth-required?* {:sms? true :user-skip? true :allow-skip? true})))
  (is (map? (auth-service/double-auth-required?* {:sms? true :user-skip? false :allow-skip? true}))))

(deftest user-exists
  (let [user-id (user-service/create-user! project-double-auth)]
    (is (not= nil (user-service/get-user user-id)))))

(deftest user-not-exists
  (is (nil? (user-service/get-user 666))))

(deftest wrap-identity-exists
  (let [user-id (user-service/create-user! project-double-auth {:email "skipper@gmail.com"})]
    (-> *s*
        (modify-session {:user-id user-id})
        (visit "/debug/request")
        (has (some-text? ":user-id"))
        (has (some-text? "skipper@gmail.com")))))

(deftest wrap-identity-not-exists
  (is (= false (-> *s*
                   (modify-session {:user-id 666})
                   (visit "/debug/session")
                   (get-in [:response :body])
                   (.contains ":user-id")))))

(deftest double-auth-to-email
  (let [user-id (create-user-with-password! {"DoubleAuthUseBoth" true
                                             "email"             "example@example.com"})]
    (binding [auth-service/double-auth-code (constantly "777666")]
      (-> *s*
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (pass-by (messages-are? [[:email "777666"]] (poll-message-chan *debug-chan*)))))))

(deftest double-auth-no-method
  (let [user-id (create-user-with-password!)]
    (-> *s*
        (visit "/login" :request-method :post :params {:username user-id :password user-id})
        (has (status? 422))
        (has (some-text? "message")))))

#_(deftest double-auth-send-fail
    (binding [debug/new-sms-in-header!      (constantly false)
                  debug/new-mail-in-header! (constantly false)]
      (-> *s*
          (visit "/login" :request-method :post :params {:username "send-fail" :password "send-fail"})
          (follow-redirect)
          (follow-redirect)
          #_(follow-redirect)
          (has (some-text? "activities")))))

(deftest double-auth-sms-priority
  (binding [auth-service/double-auth-code (constantly "777666")]
    (let [user-id (create-user-with-password! {"DoubleAuthUseBoth" true
                                               "email"             "example@example.com"
                                               "SMSNumber"         "666"})]
      (-> *s*
          (visit "/login" :request-method :post :params {:username user-id :password user-id})
          (pass-by (messages-are? [[:sms "777666"]] (poll-message-chan *debug-chan*)))))))

#_(deftest double-auth-mail-fallback
    (binding [debug/new-sms-in-header!          (constantly false)
                  auth-service/double-auth-code (constantly "777666")]
      (-> *s*
          (visit "/login" :request-method :post :params {:username "to-mail-fallback" :password "to-mail-fallback"})
          (pass-by (is (= #{{:type :email :message "777666"}} (poll-message-chan *debug-chan*)))))))

(deftest request-double-authentication
  (let [user-id (user-service/create-user! project-double-auth)]
    (user-service/update-user-properties! user-id {:username user-id
                                                   :password user-id})
    (link-user-to-treatment! user-id tx-autoaccess {})
    ;; Re-auth
    (-> *s*
        (modify-session {:user-id user-id :double-auth-code "666-666-666"})
        (visit "/user/tx/messages")
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "666-666-666"))
        (visit "/double-auth")
        (has (some-text? "666-666-666"))
        (modify-session {:double-authed? true})
        (visit "/user/tx/messages")
        (has (status? 200)))
    ;; No re-auth, external login
    (-> *s*
        (modify-session {:user-id user-id :double-auth-code "666-666-666" :external-login? true})
        (visit "/user")
        (visit "/user/tx/messages")
        (has (status? 200)))
    ;; Debug timeout
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/user/tx/messages")
        (has (status? 200))
        (visit "/debug/timeout")
        (visit "/user/tx/messages")
        (has (status? 302)))
    ;; Re-auth
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/user/tx/messages")
        (has (status? 200))
        (modify-session {:auth-re-auth? true})
        (visit "/user/tx/messages")
        (has (status? 302))
        (follow-redirect)
        (has (some-text? "Authenticate again")))
    ;; request-re-auth-ext-login
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/user")
        (visit "/user/tx/messages")
        (has (status? 200))
        (modify-session {:auth-re-auth? true :external-login? true})
        (visit "/user/tx/messages")
        (has (status? 200)))
    ;; request-re-auth-pwd
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true :auth-re-auth? true})
        (visit "/re-auth" :request-method :post :params {:password "xx"})
        (has (status? 422))
        (visit "/re-auth" :request-method :post :params {:password user-id})
        (has (status? 302)))
    ;; double auth redirect
    (is (= true (-> *s*
                    (modify-session {:user-id user-id :double-authed? true :auth-re-auth? true})
                    (visit "/re-auth" :request-method :post :params {:password user-id :return-url "/user/tx/messages"})
                    (get-in [:response :headers "Location"])
                    (.contains "/user/tx/messages"))))
    ;; double auth ajax
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true :auth-re-auth? true})
        (visit "/re-auth-ajax" :request-method :post :params {:password "xx"})
        (has (status? 422))
        (visit "/re-auth-ajax" :request-method :post :params {:password user-id})
        (has (status? 200)))
    ;;  User is not timed out and it should not matter what password is sent
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/re-auth" :request-method :post :params {:password "xx"})
        (has (status? 302)))
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/re-auth" :request-method :post :params {:password user-id})
        (has (status? 302)))
    ;; request-re-auth-timeout-path-integrity
    (fix-time
      (-> *s*
          (modify-session {:user-id user-id :double-authed? true})
          (visit "/user/tx/messages")
          (has (status? 200))
          (advance-time-s! (dec (config/env :timeout-soft)))
          (visit "/login")
          (has (status? 200))
          (advance-time-s! 1)
          (visit "/user/tx/messages")
          (has (status? 302))))
    ;; request-re-auth-timeout
    (fix-time
      (-> *s*
          (modify-session {:user-id user-id :double-authed? true})
          (visit "/user/tx/messages")
          (has (status? 200))
          (advance-time-s! (dec (config/env :timeout-soft)))
          (visit "/user/tx/messages")
          (has (status? 200))
          (advance-time-s! 1)
          (visit "/user/tx/messages")
          (has (status? 200))
          (advance-time-s! (config/env :timeout-soft))
          (visit "/user/tx/messages")
          (has (status? 302))
          (visit "/user/tx/messages")
          (has (status? 302))
          (visit "/re-auth" :request-method :post :params {:password user-id})
          (has (status? 302))
          (visit "/user/tx/messages")
          (has (status? 200))))
    ;; request-re-auth-timeout-external-login
    (fix-time
      (-> *s*
          (modify-session {:user-id user-id :double-authed? true :external-login? false})
          (visit "/user")
          (visit "/user/tx/messages")
          (has (status? 200))
          (advance-time-s! (config/env :timeout-soft))
          (visit "/user/tx/messages")
          (has (status? 302))))
    (fix-time
      (-> *s*
          (modify-session {:user-id user-id :double-authed? true :external-login? true})
          (visit "/user")
          (visit "/user/tx/messages")
          (has (status? 200))
          (advance-time-s! (config/env :timeout-soft))
          (visit "/user/tx/messages")
          (has (status? 200))))
    ;; request-re-auth-timeout-re-auth
    (fix-time
      (-> *s*
          (modify-session {:user-id user-id :double-authed? true})
          (visit "/user/tx/messages")
          (has (status? 200))
          (advance-time-s! (config/env :timeout-soft))
          (visit "/user/tx/messages")
          (follow-redirect)
          (visit "/re-auth" :request-method :post :params {:password user-id})
          (has (status? 302))
          (visit "/user")
          (visit "/user/tx/messages")
          (has (status? 200))))
    ;; request-re-auth-ajax
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true :auth-re-auth? true})
        (visit "/user/tx/messages")
        (has (status? 302))
        (visit "/user/tx/messages" :request-method :post :params {:text "xxx"})
        (has (status? 302))
        (visit "/user/tx/messages" :request-method :post :headers {"x-requested-with" "XMLHttpRequest"} :params {:text "xxx"})
        (has (status? 440)))
    ;; request logout
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/user/tx/messages")
        (has (status? 200))
        (visit "/logout")
        (visit "/user/tx/messages")
        (has (status? 403))
        (visit "/user/tx/messages")
        (has (status? 403)))
    ))

(deftest request-no-double-authentication-needed
  (let [user-id (user-service/create-user! project-no-double-auth)]
    (-> *s*
        (modify-session {:user-id user-id})
        (visit "/user/")
        (follow-redirect)
        (follow-redirect)
        (has (some-text? "activities"))
        (visit "/user/")
        (has (status? 403)))
    ;; request-no-double-authentication-visit
    (is (= true (-> *s*
                    (modify-session {:user-id user-id})
                    (visit "/double-auth")
                    (get-in [:response :headers "Location"])
                    (.contains "/user/"))))))

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


(deftest request-re-auth-pwd-unauthorized
  (-> *s*
      (visit "/re-auth" :request-method :post :params {:password "xx"})
      (has (status? 403))))

(deftest request-re-auth-pwd-ajax-unauthorized
  (-> *s*
      (visit "/re-auth-ajax" :request-method :post :params {:password "xx"})
      (has (status? 403))))

(deftest modify-session-test
  (-> *s*
      (modify-session {:test888 "hejsan"})
      (visit "/debug/session")
      (has (some-text? ":test888"))))