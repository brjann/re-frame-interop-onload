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
            [bass4.services.user :as user]))

(use-fixtures
  :once
  (fn [f]
    (mount/start
      #'bass4.config/env
      #'bass4.db.core/db-configs)
    #_(migrations/migrate ["migrate"] (select-keys env [:database-url]))
    (bass4.db.core/init-repl :db1)
    (f)))

#_(deftest test-users
  (jdbc/with-db-transaction [t-conn *db*]
    (jdbc/db-set-rollback-only! t-conn)
    (is (= 1 (db/create-user!
               t-conn
               {:id         "1"
                :first_name "Sam"
                :last_name  "Smith"
                :email      "sam.smith@example.com"
                :pass       "pass"})))
    (is (= {:id         "1"
            :first_name "Sam"
            :last_name  "Smith"
            :email      "sam.smith@example.com"
            :pass       "pass"
            :admin      nil
            :last_login nil
            :is_active  nil}
           (db/get-user t-conn {:id "1"})))))


#_(bass4.db.core/init-repl :db1)

(deftest my-foo
  (is (= 1 1)))

(deftest test-save-message
  (jdbc/with-db-transaction [t-conn *db*]
    (jdbc/db-set-rollback-only! t-conn)
    (let [message-id (save-message! 535899 "SubjectX" "Message")
          message (db/get-message-by-id {:message-id message-id})]
      (is message-id)
      (is (= "SubjectX" (:subject message)))
      (is (= 1 1))
      (is (= "Message" (:text message))))))

(defn get-edn
  [edn]
  (-> (io/file (System/getProperty "user.dir") "test/test-edns" (str edn ".edn"))
      (slurp)
      (edn/read-string)))

(deftest edn-1
  (is (= (get-edn "edn-1") {:group-name nil :group-id nil})))

(defn get-redefs-1
  []
  (with-redefs [db/get-user-by-user-id (constantly {:objectid 9})]
    (user/get-user 3443)))

(deftest redefs-1
  (is (= (get-redefs-1) {:objectid 9, :user-id 9})))

(defn get-ass-1-pending
  []
  (with-redefs [t/now (constantly (t/date-time 2017 05 30 17 16 00))
                db/get-user-group (constantly {:group-name nil :group-id nil})
                db/get-user-assessment-series (constantly {:assessment-series-id 535756})
                db/get-assessment-series-assessments (constantly (get-edn "ass-1-series-ass"))
                db/get-user-administrations (constantly (get-edn "ass-1-adms"))]
    (assessments/get-pending-assessments 535795)))

(defn get-ass-1-rounds
  [pending]
  (with-redefs [t/now (constantly (t/date-time 1986 10 14))]
    (doall (assessments/generate-assessment-round 234 pending))))

(deftest ass-1
  (let [pending (get-ass-1-pending)]
    (is (= (get-edn "ass-1-res") pending))
    (is (= (get-ass-1-rounds pending)
             (get-edn "ass-1-rounds")))))

