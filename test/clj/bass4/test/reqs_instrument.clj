(ns bass4.test.reqs-instrument
  (:require [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures debug-headers-text?]]
            [clojure.string :as string]
            [bass4.services.bass :as bass]
            [clojure.tools.logging :as log]))


#_(deftest request-post-answers
    (alter-var-root #'bass4.middleware.core/*skip-csrf* (constantly true))
    (with-redefs [bass/admin-session-file (constantly 110)]
      (-> (session (app))
          (visit "/embedded/create-session?uid=8&redirect=https://www.dn.se")
          (log/debug))))

(deftest request-post-answers
  (alter-var-root #'bass4.middleware.core/*skip-csrf* (constantly true))
  (with-redefs [bass/admin-session-file (constantly 110)]
    (-> (session (app))
        (visit "/embedded/create-session?uid=8&redirect=https://www.dn.se")
        (visit "/embedded/instrument/1647")
        (has (status? 200))
        (visit "/embedded/instrument/1647" :request-method :post :params {})
        (has (status? 400))
        (debug-headers-text? "MAIL" "schema")
        (visit "/embedded/instrument/1647" :request-method :post :params {:items "x" :specifications "y"})
        (debug-headers-text? "MAIL" "JSON")
        (has (status? 400))
        (visit "/embedded/instrument/1647" :request-method :post :params {:items "{}" :specifications "{}"})
        (has (status? 302)))))


(deftest request-wrong-instrument
  (with-redefs [bass/admin-session-file (constantly 110)]
    (-> (session (app))
        (visit "/embedded/create-session?uid=8&redirect=https://www.dn.se")
        (visit "/embedded/instrument/hell-is-here")
        (has (status? 404)))))

(deftest request-not-embedded
  (-> (session (app))
      (visit "/embedded/instrument/hell-is-here")
      (has (status? 403))))