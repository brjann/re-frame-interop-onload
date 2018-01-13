(ns bass4.test.db.core
  (:require [bass4.db.core :refer [*db*] :as db]
            [bass4.services.messages :refer [save-message!]]
            [bass4.services.assessments :as assessments]
            [luminus-migrations.core :as migrations]
            [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [bass4.config :refer [env]]
            [mount.core :as mount]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clj-time.core :as t]
            [bass4.services.user :as user]
            [bass4.test.core :refer [get-edn test-fixtures]]))


(use-fixtures
    :once
    test-fixtures)

(deftest test-save-message
  (jdbc/with-db-transaction [t-conn *db*]
    (jdbc/db-set-rollback-only! t-conn)
    (let [message-id (save-message! 535899 "Message")
          message    (db/get-message-by-id {:message-id message-id})]
      (is message-id)
      (is (= "" (:subject message)))
      (is (= 1 1))
      (is (= "Message" (:text message))))))


(deftest edn-1
  (is (= (get-edn "edn-1") {:group-name nil :group-id nil})))

#_(defn get-redefs-1
  []
  (with-redefs [db/get-user-by-user-id (constantly {:objectid 9})]
    (user/get-user 3443)))

#_(deftest redefs-1
  (is (= (get-redefs-1) {:objectid 9, :user-id 9})))

