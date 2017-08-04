(ns bass4.test.reqs-403
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [bass4.test.core :refer [test-fixtures debug-headers-text?]]
            [kerodon.test :refer :all]
            [clojure.tools.logging :as log]))

(deftest request-404-get
  (-> (session (app))
      (visit "/xxx404")
      (has (status? 404))
      (has (some-text? "go to"))))

(deftest request-403-error-post
  (-> (session (app))
      (visit "/debug/403" :request-method :post)
      (has (status? 403))))

(deftest request-403-get
  (-> (session (app))
      (visit "/debug/403")
      (has (status? 403))
      (has (some-text? "Go to"))))

(deftest request-403-get-logged-in
  (-> (session (app))
      (visit "/debug/set-session" :params {:identity 535899 :double-authed 1})
      (visit "/debug/403")
      (has (status? 403))
      (has (some-text? "go to"))))

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
      (has (text? "reload"))
      (debug-headers-text? "MAIL" "403" "/debug")))