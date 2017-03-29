(ns bass4.db.core
  (:require
    [clojure.java.jdbc :as jdbc]
    [conman.core :as conman]
    [bass4.config :refer [env]]
    [mount.core :refer [defstate]]
    [bass4.bass-locals :as locals])
  (:import [java.sql
            BatchUpdateException
            PreparedStatement]))


(defn database-urls []
  (locals/get-bass-db-configs (env :bass-path) (env :database-port)))

;; Connects to all databases in pool-specs
(defn connect!
  [pool-specs]
  (reduce merge (map (fn [pool-spec]
                       {(keyword (key pool-spec)) (conman/connect! {:jdbc-url (str (val pool-spec) "&serverTimezone=UTC")})}) pool-specs)))

;; Disconnect from all databases in db-connections
(defn disconnect!
  [db-connections]
  (map (fn [db] (conman/disconnect! (val db))) db-connections))

;; Establish connections to all databases
;; and store connections in *dbs*
(defstate ^:dynamic *dbs*
    :start (connect!
             (database-urls))
    :stop (disconnect! *dbs*))


  #_(defstate ^:dynamic *db*
           :start (conman/connect! {:jdbc-url (env :database-url)})
           :stop (conman/disconnect! *db*))

;; Bind queries to *db* dynamic variable which is bound
;; to each clients database before executing queries
;; The queries file defines the query get-user which
;; returns user by user id
(def ^:dynamic *db* nil)

(conman/bind-connection *db* "sql/bass.sql")
(conman/bind-connection *db* "sql/auth.sql")
(conman/bind-connection *db* "sql/messages.sql")
(conman/bind-connection *db* "sql/treatment.sql")

(defn to-date [^java.sql.Date sql-date]
  (-> sql-date (.getTime) (java.util.Date.)))

(extend-protocol jdbc/IResultSetReadColumn
  java.sql.Date
  (result-set-read-column [v _ _] (to-date v))

  java.sql.Timestamp
  (result-set-read-column [v _ _] (to-date v)))

(extend-type java.util.Date
  jdbc/ISQLParameter
  (set-parameter [v ^PreparedStatement stmt idx]
    (.setTimestamp stmt idx (java.sql.Timestamp. (.getTime v)))))

(defn resolve-db [request]
  (let [db-mappings (env :db-mappings)
        host (keyword (:server-name request))
        matching (if (contains? db-mappings host)
                   (get db-mappings host)
                   (:default db-mappings))]
    (if (contains? *dbs* matching)
      (get *dbs* matching)
      (throw (Exception. (str "No db present for key " matching))))))