(ns bass4.test.reqs-instrument-embedded
  (:require [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures debug-headers-text?]]
            [clojure.string :as string]
            [bass4.services.bass :as bass]
            [clojure.tools.logging :as log]))

(use-fixtures
  :once
  test-fixtures)

(deftest request-post-answers
  (with-redefs [bass/embedded-session-file (constantly {:user-id 110 :path "instrument/1647"})]
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
        (has (status? 302))
        (visit "/embedded/instrument/535690")
        (has (status? 403)))))


(deftest request-wrong-instrument
  (with-redefs [bass/embedded-session-file (constantly {:user-id 110 :path "instrument/"})]
    (-> (session (app))
        (visit "/embedded/create-session?uid=8&redirect=https://www.dn.se")
        (visit "/embedded/instrument/hell-is-here")
        (has (status? 404)))))

(deftest request-not-embedded
  (-> (session (app))
      (visit "/embedded/instrument/hell-is-here")
      (has (status? 403))))

(deftest embedded-render
  (with-redefs [bass/embedded-session-file (constantly {:user-id 110 :path "render"})]
    (-> (session (app))
        (visit "/embedded/render")
        (has (status? 403))
        (visit "/embedded/render?uid=xx&text=Hejsan")
        (has (status? 200))
        (has (some-text? "Hejsan")))))