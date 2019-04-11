(ns bass4.test.session
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
                                     api-response?
                                     log-api-response
                                     disable-attack-detector
                                     fix-time
                                     advance-time-s!
                                     *s*
                                     modify-session
                                     poll-message-chan
                                     messages-are?
                                     pass-by]]
            [clojure.core.async :refer [chan]]
            [bass4.config :as config]))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(deftest hard-timeout
  (fix-time
    (-> *s*
        (modify-session {:user-id 536975 :double-authed? true})
        (visit "/user/tx/messages")
        (has (status? 200))
        (advance-time-s! (dec (config/env :timeout-hard)))
        (visit "/user/tx/messages")
        (has (status? 302))
        (advance-time-s! 1)
        (visit "/user/tx/messages")
        (has (status? 302))
        (advance-time-s! (config/env :timeout-hard))
        (visit "/user/tx/messages")
        (has (status? 403))
        (visit "/user/tx/messages")
        (has (status? 403)))))

(deftest session-status
  (fix-time
    (-> *s*
        (visit "/api/session-status")
        (has (api-response? nil))
        (modify-session {:user-id 536975 :double-authed? true})
        (visit "/user/tx/messages")
        (has (status? 200))
        (visit "/api/session-status")
        (has (api-response? {:hard    (dec (config/env :timeout-hard))
                             :re-auth (dec (config/env :timeout-soft))}))
        (advance-time-s! 1)
        (visit "/api/session-status")
        (has (api-response? {:hard    (dec (dec (config/env :timeout-hard)))
                             :re-auth (dec (dec (config/env :timeout-soft)))}))
        (visit "/user/tx/messages")
        (visit "/api/session-status")
        (has (api-response? {:hard    (dec (config/env :timeout-hard))
                             :re-auth (dec (config/env :timeout-soft))}))
        (visit "/api/logout" :request-method :post)
        (has (status? 200))
        (visit "/api/session-status")
        (has (api-response? nil)))))

