(ns bass4.db.core
  (:require
    [clojure.java.jdbc :as jdbc]
    [bass4.utils :refer [map-map]]
    [conman.core :as conman]
    [bass4.config :refer [env]]
    [mount.core :refer [defstate]]
    [bass4.bass-locals :as locals]
    [clojure.tools.logging :as log])
  (:import [java.sql
            BatchUpdateException
            PreparedStatement]))


(defn connect!
  [db-config]
  (map-map
    (fn [pool-spec]
      (assoc pool-spec :db-conn
               (delay
                 (do
                   (log/info (str "Attaching " (:db-url pool-spec)))
                   ;;TODO: &serverTimezone=UTC WTF????
                   (conman/connect! {:jdbc-url (str (:db-url pool-spec) "&serverTimezone=UTC")})))))
       db-config))

(defn database-configs []
  (locals/get-bass-db-configs (env :bass-path) (env :database-port)))

;; Disconnect from all databases in db-connections
(defn disconnect!
  [db-connections]
  (doall
    (map (fn [db]
           (let [db-conn (:db-conn (val db))]
             (when (realized? db-conn)
               (do
                 (log/info (str "Detaching " (key db)))
                 (conman/disconnect! @db-conn)))))
         db-connections)))


;; Establish connections to all databases
;; and store connections in *dbs*
(defstate ^:dynamic *dbs*
    :start (connect!
             (database-configs))
    :stop (disconnect! *dbs*))

;; Bind queries to *db* dynamic variable which is bound
;; to each clients database before executing queries
(def ^:dynamic *db* nil)

(conman/bind-connection *db* "sql/bass.sql")
(conman/bind-connection *db* "sql/auth.sql")
(conman/bind-connection *db* "sql/messages.sql")
(conman/bind-connection *db* "sql/treatments.sql")
(conman/bind-connection *db* "sql/instruments.sql")
(conman/bind-connection *db* "sql/assessments.sql")
(conman/bind-connection *db* "sql/instrument-answers.sql")

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
        ;;host (keyword (:server-name request))
        host (keyword
               (first (filter identity
                              [(get-in request [:headers "x-forwarded-host"])
                               (:server-name request)])))
        matching (if (contains? db-mappings host)
                   (get db-mappings host)
                   (:default db-mappings))]
    (if (contains? *dbs* matching)
      (get *dbs* matching)
      (throw (Exception. (str "No db present for key " matching " mappings: " db-mappings))))))