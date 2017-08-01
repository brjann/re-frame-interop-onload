(ns bass4.test.reqs-auth
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.services.auth :as auth-service]))

(deftest double-auth-required
  (is (= false (auth-service/double-auth-required? 7)))
  (is (= false (auth-service/double-auth-required? 536821)))
  (is (= true (auth-service/double-auth-required? 535759)))
  (is (= false (auth-service/double-auth-required? 536834)))
  (is (= true (auth-service/double-auth-required? 536835))))

(deftest request-double-authentication
  (-> (session (app))
      (visit "/debug/set-session" :params {:identity 535899 :double-auth-code "666-666-666"})
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
  (-> (session (app))
      (visit "/debug/set-session" :params {:identity 536821})
      (visit "/double-auth")
      (get-in [:response :headers "Location"])
      (.contains "/user/")))

(deftest request-403
  (-> (session (app))
      (visit "/user/messages")
      (has (status? 403))))

(deftest request-re-auth
  (-> (session (app))
      (visit "/debug/set-session" :params {:identity 535899 :double-authed 1})
      (visit "/user/messages")
      (has (status? 200))
      (visit "/debug/set-session" :params {:auth-timeout true})
      (visit "/user/messages")
      (has (status? 302))
      (follow-redirect)
      (has (some-text? "Authenticate again"))))

(deftest request-re-auth-pwd
  (-> (session (app))
      (visit "/debug/set-session" :params {:identity 535899 :double-authed 1 :auth-timeout true})
      (visit "/re-auth" :request-method :post :params {:password 53589})
      (has (status? 422))
      (visit "/re-auth" :request-method :post :params {:password 535899})
      (has (status? 302))))

(deftest request-re-auth-pwd-redirect
  (is (= true (-> (session (app))
                  (visit "/debug/set-session" :params {:identity 535899 :double-authed 1 :auth-timeout true})
                  (visit "/re-auth" :request-method :post :params {:password 535899 :return-url "/user/messages"})
                  (get-in [:response :headers "Location"])
                  (.contains "/user/messages")))))

(deftest request-re-auth-pwd-ajax
  (-> (session (app))
      (visit "/debug/set-session" :params {:identity 535899 :double-authed 1 :auth-timeout true})
      (visit "/re-auth-ajax" :request-method :post :params {:password 53589})
      (has (status? 422))
      (visit "/re-auth-ajax" :request-method :post :params {:password 535899})
      (has (status? 200))))

(deftest request-re-auth-pwd-unauthorized
  (-> (session (app))
      (visit "/re-auth" :request-method :post :params {:password 535899})
      (has (status? 403))))

(deftest request-re-auth-pwd-ajax-unauthorized
  (-> (session (app))
      (visit "/re-auth-ajax" :request-method :post :params {:password 535899})
      (has (status? 403))))

(deftest request-re-auth-pwd-unnecessary-wrong
  "User is not timed out and it should not matter what password is sent"
  (-> (session (app))
      (visit "/debug/set-session" :params {:identity 535899 :double-authed 1})
      (visit "/re-auth" :request-method :post :params {:password 23254})
      (has (status? 302))))

(deftest request-re-auth-pwd-unnecessary-right
  "User is not timed out and it should not matter what password is sent"
  (-> (session (app))
      (visit "/debug/set-session" :params {:identity 535899 :double-authed 1})
      (visit "/re-auth" :request-method :post :params {:password 535899})
      (has (status? 302))))