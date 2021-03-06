(ns ^:eftest/synchronized
  bass4.test.db-config
  (:require [clojure.test :refer :all]
            [bass4.config :as config]
            [bass4.test.core :refer [test-fixtures
                                     fn-not-text?
                                     log-return
                                     log-body
                                     log-headers
                                     log-status
                                     disable-attack-detector
                                     *s*]]
            [clojure.tools.logging :as log]
            [bass4.clients.core :as clients]))

(use-fixtures
  :once
  test-fixtures)

(defn test-db []
  (:test-db config/env))

(deftest debug-mode
  (with-redefs [config/env (merge config/env
                                  {:dev         false
                                   :test        false
                                   :debug-mode  false
                                   :db-settings {(test-db) {:debug-mode false}}})]
    (is (= false (clients/debug-mode?))))

  (with-redefs [config/env (merge config/env
                                  {:dev         true
                                   :test        false
                                   :debug-mode  false
                                   :db-settings {(test-db) {:debug-mode false}}})]
    (is (= true (clients/debug-mode?))))

  (with-redefs [config/env (merge config/env
                                  {:dev         false
                                   :test        true
                                   :debug-mode  false
                                   :db-settings {(test-db) {:debug-mode false}}})]
    (is (= false (clients/debug-mode?))))

  (with-redefs [config/env (merge config/env
                                  {:dev         false
                                   :test        false
                                   :debug-mode  true
                                   :db-settings {(test-db) {:debug-mode false}}})]
    (is (= false (clients/debug-mode?))))

  (with-redefs [config/env (merge config/env
                                  {:dev         false
                                   :test        false
                                   :debug-mode  false
                                   :db-settings {(test-db) {:debug-mode true}}})]
    (is (= true (clients/debug-mode?)))))

(deftest db-settings
  (with-redefs [config/env (merge config/env
                                  {:setting-666 666
                                   :db-settings {(test-db) {}}})]
    (is (= 666 (clients/client-setting [:setting-666]))))

  (with-redefs [config/env (merge config/env
                                  {:db-settings {(test-db) {}}})]
    #_(is (= :thrown (try
                       (do (clients/client-setting [:setting-666])
                           false)
                       (catch Exception _
                       :thrown))))
    (is (= :default (clients/client-setting [:setting-666] :default))))

  (with-redefs [config/env (merge config/env
                                  {:db-settings {(test-db) {:setting-666 666}}})]
    (is (= 666 (clients/client-setting [:setting-666] :default))))

  (with-redefs [config/env (merge config/env
                                  {:setting-666 333
                                   :db-settings {(test-db) {:setting-666 666}}})]
    (is (= 666 (clients/client-setting [:setting-666] :default)))))