(ns bass4.test.reqs-instrument-embedded
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [chan]]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures
                                     debug-headers-text?
                                     disable-attack-detector
                                     *s*
                                     pass-by
                                     messages-are?
                                     poll-message-chan]]
            [clojure.string :as string]
            [bass4.services.bass :as bass]
            [bass4.external-messages :refer [*debug-chan*]]
            [clojure.tools.logging :as log]))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(use-fixtures
  :each
  (fn [f]
    (binding [*debug-chan* (chan)]
      (f))))

(deftest request-post-answers
  (with-redefs [bass/read-session-file (constantly {:user-id 110 :path "instrument/1647"})]
    (-> *s*
        (visit "/embedded/create-session?uid=8&redirect=https://www.dn.se")
        (visit "/embedded/instrument/1647")
        (has (status? 200))
        (visit "/embedded/instrument/1647" :request-method :post :params {})
        (has (status? 400))
        (pass-by (messages-are? [[:email "JSON"]] (poll-message-chan *debug-chan*)))
        (visit "/embedded/instrument/1647" :request-method :post :params {:items "x" :specifications "y"})
        (pass-by (messages-are? [[:email "JSON"]] (poll-message-chan *debug-chan*)))
        (has (status? 400))
        (visit "/embedded/instrument/1647" :request-method :post :params {:items "{}" :specifications "{}"})
        (has (status? 302))
        (visit "/embedded/instrument/535690")
        (has (status? 403)))))


(deftest request-wrong-instrument
  (with-redefs [bass/read-session-file (constantly {:user-id 110 :path "instrument/"})]
    (-> *s*
        (visit "/embedded/create-session?uid=8&redirect=https://www.dn.se")
        (visit "/embedded/instrument/hell-is-here")
        (has (status? 400))
        (visit "/embedded/instrument/666")
        (has (status? 404)))))

(deftest request-not-embedded
  (-> *s*
      (visit "/embedded/instrument/hell-is-here")
      (has (status? 403))))

(deftest embedded-render
  (with-redefs [bass/read-session-file (constantly {:user-id 110 :path "render"})]
    (-> *s*
        (visit "/embedded/render")
        (has (status? 403))
        (visit "/embedded/render?uid=x" :request-method :post :params {:text "Hejsan"})
        (has (status? 200))
        (has (some-text? "Hejsan")))))