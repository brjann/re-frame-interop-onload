(ns bass4.test.reqs-embedded
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [chan]]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [bass4.test.core :refer [test-fixtures
                                     debug-headers-text?
                                     disable-attack-detector
                                     log-headers
                                     *s*
                                     pass-by
                                     messages-are?
                                     fix-time
                                     log-body
                                     advance-time-s!
                                     poll-message-chan]]
            [clojure.string :as string]
            [bass4.services.bass :as bass]
            [bass4.external-messages :refer [*debug-chan*]]
            [clojure.tools.logging :as log]
            [bass4.time :as b-time]
            [clj-time.core :as t]
            [clojure.java.jdbc :as jdbc]
            [bass4.db.core :as db]
            [bass4.services.bass :as bass-service]
            [bass4.utils :as utils])
  (:import (java.util UUID)))

(use-fixtures
  :once
  test-fixtures
  disable-attack-detector)

(use-fixtures
  :each
  (fn [f]
    (binding [*debug-chan* (chan)]
      (f))))

(defn get-php-session-id
  []
  (subs (str (UUID/randomUUID)) 0 32))

(deftest wrong-uid
  (with-redefs [bass/read-session-file (constantly {:user-id nil :path "instrument/1647" :php-session-id nil})]
    (-> *s*
        (visit "/embedded/create-session?uid=8&redirect=https://www.dn.se")
        (has (some-text? "Wrong uid")))))

(deftest request-post-answers
  (let [php-session-id (get-php-session-id)
        now            (b-time/to-unix (t/now))]
    (jdbc/insert! db/*db* "sessions" {"SessId" php-session-id "UserId" 110 "LastActivity" now "SessionStart" now})
    (with-redefs [bass/read-session-file (constantly {:user-id 110 :path "instrument/1647" :php-session-id php-session-id})]
      (-> *s*
          (visit "/embedded/create-session?uid=8&redirect=https://www.dn.se")
          (visit "/embedded/instrument/1647")
          (has (status? 200))
          (visit "/embedded/instrument/1647" :request-method :post :params {})
          (has (status? 400))
          (pass-by (messages-are? [[:email "nil error"]] (poll-message-chan *debug-chan*)))
          (visit "/embedded/instrument/1647" :request-method :post :params {:items "x" :specifications "y"})
          (pass-by (messages-are? [[:email "api/->json"]] (poll-message-chan *debug-chan*)))
          (has (status? 400))
          (visit "/embedded/instrument/1647" :request-method :post :params {:items "{}" :specifications "{}"})
          (has (status? 302))
          (visit "/embedded/instrument/535690")
          (has (status? 403))))))


(deftest request-wrong-instrument
  (with-redefs [bass/read-session-file (constantly {:user-id 110 :path "instrument/" :php-session-id "xxx"})
                bass/get-php-session   (constantly {:user-id 110 :last-activity (b-time/to-unix (t/now))})]
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
  (with-redefs [bass/read-session-file (constantly {:user-id 110 :path "iframe/render" :php-session-id "xxx"})
                bass/get-php-session   (constantly {:user-id 110 :last-activity (b-time/to-unix (t/now))})]
    (-> *s*
        (visit "/embedded/iframe/render")
        (has (status? 403))
        (visit "/embedded/iframe/render?uid=x" :request-method :post :params {:text "Hejsan"})
        (has (status? 200))
        (has (some-text? "Hejsan")))))

(deftest session-timeout
  (fix-time
    (let [php-session-id   (get-php-session-id)
          now              (utils/to-unix (t/now))
          timeouts         (bass-service/get-staff-timeouts)
          re-auth-timeout  (:re-auth-timeout timeouts)
          absolute-timeout (:absolute-timeout timeouts)]
      (jdbc/insert! db/*db* "sessions" {"SessId" php-session-id "UserId" 110 "LastActivity" now "SessionStart" now})
      (with-redefs [bass/read-session-file (constantly {:user-id 110 :path "instrument/1647" :php-session-id php-session-id})]
        (-> *s*
            (visit "/embedded/create-session?uid=8")
            (visit "/embedded/instrument/1647")
            (has (status? 200))
            ;; Advance time to re-auth-timeout
            (advance-time-s! re-auth-timeout)
            (visit "/embedded/instrument/1647")
            (follow-redirect)
            (has (some-text? "Timeout"))
            ;; Fake re-auth
            (pass-by (bass-service/update-php-session-last-activity! php-session-id (utils/to-unix (t/now))))
            (visit "/embedded/instrument/1647")
            (has (status? 200))
            ;; Advance time to re-auth-timeout in two steps
            (advance-time-s! (dec re-auth-timeout))
            (advance-time-s! 1)
            (visit "/embedded/instrument/1647")
            (has (status? 302))
            (pass-by (bass-service/update-php-session-last-activity! php-session-id (utils/to-unix (t/now))))
            ;; Advance time to almost re-auth-timeout
            (advance-time-s! (dec re-auth-timeout))
            ;; Reload page (updating last activity)
            (visit "/embedded/instrument/1647")
            (has (status? 200))
            ;; Advance time to full re-auth-timeout
            (advance-time-s! 1)
            ;; Still logged in because of previous page reload
            (visit "/embedded/instrument/1647")
            (has (status? 200))
            ;; Advance time to absolute-timeout
            (advance-time-s! absolute-timeout)
            (visit "/embedded/instrument/1647")
            (follow-redirect)
            (has (some-text? "No session"))
            ;; Reload page (updating last activity)
            (pass-by (bass-service/update-php-session-last-activity! php-session-id (utils/to-unix (t/now))))
            (visit "/embedded/instrument/1647")
            ;; Access error - session was destroyed
            (has (status? 403)))))))

(deftest path-merge
  (fix-time
    (let [php-session-id-1 (get-php-session-id)
          php-session-id-2 (get-php-session-id)
          now              (utils/to-unix (t/now))
          session-files    (atom [{:user-id 110 :path "instrument/286" :php-session-id php-session-id-2}
                                  {:user-id 110 :path "instrument/286" :php-session-id php-session-id-1}
                                  {:user-id 110 :path "instrument/1647" :php-session-id php-session-id-1}])]
      (jdbc/insert! db/*db* "sessions" {"SessId" php-session-id-1 "UserId" 110 "LastActivity" now "SessionStart" now})
      (jdbc/insert! db/*db* "sessions" {"SessId" php-session-id-2 "UserId" 110 "LastActivity" now "SessionStart" now})
      (with-redefs [bass/read-session-file (fn [_] (utils/queue-pop! session-files))]
        (-> *s*
            ;; First session file gives access to 1647
            (visit "/embedded/create-session?uid=8")
            (visit "/embedded/instrument/1647")
            (has (status? 200))
            ;; No access to 286 yet
            (visit "/embedded/instrument/286")
            (has (status? 403))
            ;; First session file gives access to 286
            (visit "/embedded/create-session?uid=8")
            (visit "/embedded/instrument/286")
            (has (status? 200))
            ;; Access to 1647 has been kept
            (visit "/embedded/instrument/1647")
            (has (status? 200))
            ;; Third session file changes session id and gives access to 286
            (visit "/embedded/create-session?uid=8")
            (visit "/embedded/instrument/286")
            (has (status? 200))
            ;; No access to 1647 because of changed session id
            (visit "/embedded/instrument/1647")
            (has (status? 403)))))))