(ns bass4.test.reqs-api
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
                                     *s*
                                     modify-session
                                     poll-message-chan
                                     messages-are?
                                     pass-by]]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]))


(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(deftest request-api-re-auth
  (-> *s*
      (modify-session {:user-id 536975 :double-authed? true})
      (modify-session {:last-request-time (t/date-time 1985 10 26 1 20 0 0)})
      (visit "/api/user/tx/messages")
      (has (status? 440))
      (visit "/re-auth" :request-method :post :params {:password 536975})
      (visit "/user/tx/messages")
      (has (status? 200))))