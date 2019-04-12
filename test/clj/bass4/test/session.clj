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
            [bass4.config :as config]
            [bass4.session.create :as session-create]))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(deftest hard-timeout
  (fix-time
    (-> *s*
        (modify-session (session-create/new {:user-id 536975} {:double-authed? true}))
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

(deftest session-status-time-passes-re-auth
  (let [timeout-hard    (config/env :timeout-hard)
        timeout-re-auth (config/env :timeout-soft)]
    (fix-time
      (-> *s*
          (visit "/api/session-status")
          (has (api-response? nil))
          (modify-session (session-create/new {:user-id 536975} {:double-authed? true}))
          (visit "/api/user/tx/messages")
          (has (status? 200))
          (visit "/api/session-status")
          (has (api-response? {:hard    timeout-hard
                               :re-auth timeout-re-auth}))
          (advance-time-s! 1)
          (visit "/api/session-status")
          (has (api-response? {:hard    (dec timeout-hard)
                               :re-auth (dec timeout-re-auth)}))
          (visit "/user/tx/messages")
          (visit "/api/session-status")
          (has (api-response? {:hard    timeout-hard
                               :re-auth timeout-re-auth}))
          (advance-time-s! timeout-re-auth)
          (visit "/api/session-status")
          (has (api-response? {:hard    (- timeout-hard timeout-re-auth)
                               :re-auth 0}))
          (visit "/api/user/tx/messages")
          (has (status? 440))))))

(deftest session-status-time-passes-hard
  (let [timeout-hard    (config/env :timeout-hard)
        timeout-re-auth (config/env :timeout-soft)]
    (fix-time
      (-> *s*
          (visit "/api/session-status")
          (has (api-response? nil))
          (modify-session (session-create/new {:user-id 536975} {:double-authed? true}))
          (visit "/api/user/tx/messages")
          (has (status? 200))
          (visit "/api/session-status")
          (has (api-response? {:hard    timeout-hard
                               :re-auth timeout-re-auth}))
          (advance-time-s! (dec timeout-hard))
          (visit "/api/session-status")
          (has (api-response? {:hard    1
                               :re-auth 0}))
          (visit "/user/tx/messages")
          (has (status? 302))
          (visit "/api/session-status")
          (advance-time-s! (dec timeout-hard))
          (visit "/api/session-status")
          (has (api-response? {:hard    1
                               :re-auth 0}))
          (advance-time-s! 1)
          (visit "/api/session-status")
          (has (api-response? nil))
          (visit "/api/user/tx/messages")
          (has (status? 403))))))

(deftest session-status-logout
  (let [timeout-hard    (config/env :timeout-hard)
        timeout-re-auth (config/env :timeout-soft)]
    (fix-time
      (-> *s*
          (visit "/api/session-status")
          (has (api-response? nil))
          (modify-session {:user-id 536975 :double-authed? true})
          (visit "/user/tx/messages")
          (has (status? 200))
          (visit "/api/session-status")
          (has (api-response? {:hard    timeout-hard
                               :re-auth timeout-re-auth}))
          (visit "/api/logout" :request-method :post)
          (has (status? 200))
          (visit "/api/session-status")
          (has (api-response? nil))))))

(deftest session-status-no-re-auth-path
  (let [timeout-hard    (config/env :timeout-hard)
        timeout-re-auth (config/env :timeout-soft)]
    (fix-time
      (-> *s*
          (visit "/api/session-status")
          (has (api-response? nil))
          (modify-session (session-create/new {:user-id 536975} {:double-authed? true}))
          (visit "/api/session-status")
          (has (api-response? {:hard    timeout-hard
                               :re-auth timeout-re-auth}))))))

(deftest session-status-ext-login
  (let [timeout-hard (config/env :timeout-hard)]
    (fix-time
      (-> *s*
          (visit "/api/session-status")
          (has (api-response? nil))
          (modify-session (session-create/new {:user-id 536975}
                                              {:double-authed?  true
                                               :external-login? true}))
          (visit "/user/tx/messages")
          (has (status? 200))
          (visit "/api/session-status")
          (has (api-response? {:hard    timeout-hard
                               :re-auth nil}))))))