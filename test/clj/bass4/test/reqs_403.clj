(ns bass4.test.reqs-403
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [clojure.core.async :refer [chan]]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [bass4.test.core :refer :all]
            [bass4.external-messages.async :refer [*debug-chan*]]
            [kerodon.test :refer :all]
            [clojure.tools.logging :as log]
            [bass4.services.user :as user-service]))

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
  (let [user-id (user-service/create-user! project-double-auth)]
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/debug/403")
        (has (status? 403))
        (has (some-text? "go to")))))

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

(deftest request-403-ajax-post-reload
  (let [user-id (user-service/create-user! project-double-auth)]
    (-> *s*
        (modify-session {:user-id user-id :double-authed? true})
        (visit "/debug/403" :request-method :post :headers {"x-requested-with" "XMLHttpRequest"})
        (has (status? 403))
        (has (some-text? "reload")))))