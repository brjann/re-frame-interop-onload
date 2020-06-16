(ns bass4.test.session
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer :all]
            [clojure.core.async :refer [chan]]
            [bass4.session.create :as session-create]
            [bass4.session.timeout :as session-timeout]
            [clojure.tools.logging :as log]))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(deftest not-found
  (let [user-id (create-user-with-treatment! tx-autoaccess)]
    (-> *s*
        (modify-session (session-create/new {:user-id user-id} {:double-authed? true}))
        (visit "/api/session/xxx")
        (has (status? 404)))))

(deftest hard-timeout
  (let [user-id      (create-user-with-treatment! tx-autoaccess)
        timeout-hard (session-timeout/timeout-hard-limit)]
    (fix-time
      (-> *s*
          (modify-session (session-create/new {:user-id user-id} {:double-authed? true}))
          (visit "/user/tx/messages")
          (has (status? 200))
          (advance-time-s! (dec timeout-hard))
          (visit "/user/tx/messages")
          (has (status? 302))
          (advance-time-s! 1)
          (visit "/user/tx/messages")
          (has (status? 302))
          (advance-time-s! timeout-hard)
          (visit "/user/tx/messages")
          (has (status? 403))
          (visit "/user/tx/messages")
          (has (status? 403))))))

(deftest session-status-time-passes-re-auth
  (let [user-id         (create-user-with-treatment! tx-autoaccess)
        timeout-hard    (session-timeout/timeout-hard-limit)
        timeout-re-auth (session-timeout/timeout-re-auth-limit)]
    (fix-time
      (-> *s*
          (visit "/api/session/status")
          (has (api-response? nil))
          (modify-session (session-create/new {:user-id user-id} {:double-authed? true}))
          (visit "/api/user/tx/messages")
          (has (status? 200))
          (visit "/api/session/status")
          (has (api-response? {:hard    timeout-hard
                               :re-auth timeout-re-auth}))
          (advance-time-s! 1)
          (visit "/api/session/status")
          (has (api-response? {:hard    (dec timeout-hard)
                               :re-auth (dec timeout-re-auth)}))
          (visit "/user/tx/messages")
          (visit "/api/session/status")
          (has (api-response? {:hard    timeout-hard
                               :re-auth timeout-re-auth}))
          (advance-time-s! timeout-re-auth)
          (visit "/api/session/status")
          (has (api-response? {:hard    (- timeout-hard timeout-re-auth)
                               :re-auth 0}))
          (visit "/api/user/tx/messages")
          (has (status? 440))))))

(deftest session-status-time-passes-hard
  (let [user-id         (create-user-with-treatment! tx-autoaccess)
        timeout-hard    (session-timeout/timeout-hard-limit)
        timeout-re-auth (session-timeout/timeout-re-auth-limit)]
    (fix-time
      (-> *s*
          (visit "/api/session/status")
          (has (api-response? nil))
          (modify-session (session-create/new {:user-id user-id} {:double-authed? true}))
          (visit "/api/user/tx/messages")
          (has (status? 200))
          (visit "/api/session/status")
          (has (api-response? {:hard    timeout-hard
                               :re-auth timeout-re-auth}))
          (advance-time-s! (dec timeout-hard))
          (visit "/api/session/status")
          (has (api-response? {:hard    1
                               :re-auth 0}))
          (visit "/user/tx/messages")
          (has (status? 302))
          (visit "/api/session/status")
          (advance-time-s! (dec timeout-hard))
          (visit "/api/session/status")
          (has (api-response? {:hard    1
                               :re-auth 0}))
          (advance-time-s! 1)
          (visit "/api/session/status")
          (has (api-response? nil))
          (visit "/api/user/tx/messages")
          (has (status? 403))))))

(deftest session-status-logout
  (let [user-id         (create-user-with-treatment! tx-autoaccess)
        timeout-hard    (session-timeout/timeout-hard-limit)
        timeout-re-auth (session-timeout/timeout-re-auth-limit)]
    (fix-time
      (-> *s*
          (visit "/api/session/status")
          (has (api-response? nil))
          (modify-session {:user-id user-id :double-authed? true})
          (visit "/user/tx/messages")
          (has (status? 200))
          (visit "/api/session/status")
          (has (api-response? {:hard    timeout-hard
                               :re-auth timeout-re-auth}))
          (visit "/api/logout" :request-method :post)
          (has (status? 200))
          (visit "/api/session/status")
          (has (api-response? nil))))))

(deftest session-status-no-re-auth-path
  (let [user-id         (create-user-with-treatment! tx-autoaccess)
        timeout-hard    (session-timeout/timeout-hard-limit)
        timeout-re-auth (session-timeout/timeout-re-auth-limit)]
    (fix-time
      (-> *s*
          (visit "/api/session/status")
          (has (api-response? nil))
          (modify-session (session-create/new {:user-id user-id} {:double-authed? true}))
          (visit "/api/session/status")
          (has (api-response? {:hard    timeout-hard
                               :re-auth timeout-re-auth}))))))

(deftest session-status-ext-login
  (let [user-id      (create-user-with-treatment! tx-autoaccess)
        timeout-hard (session-timeout/timeout-re-auth-limit)]
    ;; re-auth timelimit used when user cannot re-auth
    (fix-time
      (-> *s*
          (visit "/api/session/status")
          (has (api-response? nil))
          (modify-session (session-create/new {:user-id user-id}
                                              {:double-authed?  true
                                               :external-login? true}))
          (visit "/user/tx/messages")
          (has (status? 200))
          (visit "/api/session/status")
          (has (api-response? {:hard    timeout-hard
                               :re-auth nil}))))))

(deftest session-timeout-modification
  (let [user-id         (create-user-with-treatment! tx-autoaccess)
        timeout-hard    (session-timeout/timeout-hard-limit)
        timeout-re-auth (session-timeout/timeout-re-auth-limit)]
    (fix-time
      (-> *s*
          (visit "/api/session/status")
          (has (api-response? nil))
          (modify-session (session-create/new {:user-id user-id} {:double-authed? true}))
          (visit "/api/user/tx/messages")
          (has (status? 200))
          (visit "/api/session/status")
          (has (api-response? {:hard    timeout-hard
                               :re-auth timeout-re-auth}))
          (visit "/api/session/timeout-re-auth")
          (has (status? 200))
          (visit "/api/session/status")
          (has (api-response? {:hard    timeout-hard
                               :re-auth 0}))
          (visit "/api/user/tx/messages")
          (has (status? 440))))))

(deftest session-user-id
  (let [user-id (create-user-with-treatment! tx-autoaccess)]
    (-> *s*
        (visit "/api/session/user-id")
        (has (api-response? nil))
        (modify-session (session-create/new {:user-id user-id} {:double-authed? true}))
        (visit "/api/user/tx/messages")
        (has (status? 200))
        (visit "/api/session/user-id")
        (has (api-response? {:user-id user-id})))))

(deftest session-timeout-timeout-soon
  (let [user-id           (create-user-with-treatment! tx-autoaccess true)
        timeout-hard      (session-timeout/timeout-hard-limit)
        timeout-re-auth   (session-timeout/timeout-re-auth-limit)
        timeout-hard-soon (session-timeout/timeout-hard-soon-limit)]
    (log/debug timeout-hard-soon)
    (fix-time
      (-> *s*
          (visit "/api/session/status")
          (has (api-response? nil))
          (modify-session (session-create/new {:user-id user-id} {:double-authed? true}))
          (visit "/api/user/tx/messages")
          (has (status? 200))
          (visit "/api/session/status")
          (has (api-response? {:hard    timeout-hard
                               :re-auth timeout-re-auth}))
          (visit "/api/session/timeout-hard-soon")
          (has (status? 200))
          (visit "/api/session/status")
          (has (api-response? {:hard    timeout-hard-soon
                               :re-auth 0}))
          (visit "/api/user/tx/messages")
          (has (status? 440))
          (visit "/api/session/renew")
          (has (status? 400))
          (advance-time-s! 1)
          (visit "/re-auth" :request-method :post :params {:password "wrong"})
          (has (status? 422))
          (visit "/api/session/status")
          ;; Timeout should not reset because request was made
          (has (api-response? {:hard    (dec timeout-hard-soon)
                               :re-auth 0}))
          (visit "/re-auth" :request-method :post :params {:password user-id})
          (has (status? 302))
          (visit "/api/session/status")
          ;; Timeout should now be reset
          (has (api-response? {:hard    timeout-hard
                               :re-auth timeout-re-auth}))
          (visit "/api/user/tx/messages")
          (has (status? 200))
          ;; API re-auth
          (visit "/api/session/timeout-re-auth")
          (has (status? 200))
          (visit "/api/user/tx/messages")
          (has (status? 440))
          (visit "/api/re-auth" :request-method :post :body-params {:password "wrong"})
          (has (status? 422))
          (visit "/api/re-auth" :request-method :post :body-params {:password (str user-id)})
          (has (status? 200))
          (visit "/api/user/tx/messages")
          (has (status? 200))))

    (fix-time
      ;; Test bug where session timeout was not activated immediately
      (-> *s*
          (modify-session (session-create/new {:user-id user-id} {:double-authed? true}))
          (visit "/api/session/timeout-re-auth")
          (has (status? 200))
          (visit "/api/re-auth" :request-method :post :body-params {:password "wrong"})
          (has (status? 422))
          (visit "/api/re-auth" :request-method :post :body-params {:password (str user-id)})
          (has (status? 200))
          (visit "/api/user/tx/messages")
          (has (status? 200))))

    (fix-time
      (-> *s*
          (visit "/api/session/status")
          (has (api-response? nil))
          (modify-session (session-create/new {:user-id user-id} {:external-login? true}))
          (visit "/api/user/tx/messages")
          (has (status? 200))
          (visit "/api/session/status")
          ;; Uses re-auth timeout as hard timeout when user cannot re-auth
          (has (api-response? {:hard    timeout-re-auth
                               :re-auth nil}))
          (visit "/api/session/timeout-hard-soon")
          (has (status? 200))
          (visit "/api/session/status")
          (has (api-response? {:hard    timeout-hard-soon
                               :re-auth nil}))
          (visit "/api/session/renew")
          (has (status? 200))
          (visit "/api/session/status")
          (has (api-response? {:hard    timeout-re-auth
                               :re-auth nil}))))))