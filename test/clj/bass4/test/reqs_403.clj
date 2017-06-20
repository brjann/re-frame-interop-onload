(ns bass4.test.reqs-403
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]))


(deftest request-403-error
  (-> (session (app))
      (visit "/debug/403" :request-method :post :params {:password 535899})
      (has (status? 403))))

(deftest request-found
  (-> (session (app))
      (visit "/debug/found" :request-method :post)
      (has (status? 302))))

(deftest request-found-ajax
  (-> (session (app))
      (visit "/debug/found" :request-method :post :headers {"x-requested-with" "XMLHttpRequest"})
      (has (status? 200))))

(deftest request-403-ajax
  (-> (session (app))
      (visit "/debug/403" :request-method :post :headers {"x-requested-with" "XMLHttpRequest"})
      (has (status? 403))
      (has (text? "login"))))

(deftest request-403-ajax-with-identity
  (-> (session (app))
      (visit "/debug/set-session" :params {:identity 535899 :double-authed 1})
      (visit "/debug/403" :request-method :post :headers {"x-requested-with" "XMLHttpRequest"})
      (has (status? 403))
      (has (text? "reload"))))