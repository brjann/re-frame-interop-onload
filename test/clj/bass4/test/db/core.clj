(ns bass4.test.db.core
  (:require [bass4.db.core :refer [*db*] :as db]
            [bass4.services.messages :refer [save-message!]]
            [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [bass4.config :refer [env]]
            [clojure.tools.logging :as log]
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