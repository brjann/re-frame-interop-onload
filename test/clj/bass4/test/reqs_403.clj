(ns bass4.test.reqs-403
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [bass4.test.core :refer [test-fixtures debug-headers-text? *s* modify-session]]
            [kerodon.test :refer :all]
            [bass4.middleware.debug :as mw-debug]
            [bass4.test.core :refer [get-edn test-fixtures]]
            [clojure.tools.logging :as log]))

(use-fixtures
  :once
  test-fixtures)

(deftest request-404-get
  (-> *s*
      (visit "/xxx404")
      (has (status? 404))
      (has (some-text? "go to"))))

(deftest request-403-error-post
  (-> *s*
      (visit "/debug/403" :request-method :post)
      (has (status? 403))))

(deftest request-403-get
  (-> *s*
      (visit "/debug/403")
      (has (status? 403))
      (has (some-text? "Go to"))))

(deftest request-403-get-logged-in
  (-> *s*
      (modify-session {:identity 535771 :double-authed? true})
      (visit "/debug/403")
      (has (status? 403))
      (has (some-text? "go to"))))

(deftest request-found
  (-> *s*
      (visit "/debug/found" :request-method :post)
      (has (status? 302))))

(deftest request-found-ajax
  (-> *s*
      (visit "/debug/found" :request-method :post :headers {"x-requested-with" "XMLHttpRequest"})
      (has (status? 200))))

(deftest request-403-ajax
  (-> *s*
      (visit "/debug/403" :request-method :post :headers {"x-requested-with" "XMLHttpRequest"})
      (has (status? 403))
      (has (text? "login"))))

(deftest request-403-ajax-with-identity
  (-> *s*
      (modify-session {:identity 535771 :double-authed? true})
      (visit "/debug/403" :request-method :post :headers {"x-requested-with" "XMLHttpRequest"})
      (has (status? 403))
      (has (text? "reload"))
      (debug-headers-text? "MAIL" "403" "/debug")))