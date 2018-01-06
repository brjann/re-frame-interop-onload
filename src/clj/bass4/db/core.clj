(ns bass4.db.core
  (:require
    [clojure.java.jdbc :as jdbc]
    [bass4.utils :refer [map-map filter-map time+ val-to-bool]]
    [conman.core :as conman]
    [bass4.config :refer [env]]
    [mount.core :refer [defstate]]
    [bass4.bass-locals :as locals]
    [clojure.tools.logging :as log]
    [bass4.request-state :as request-state]
    [bass4.bass-locals :as bass-locals])
  (:import [java.sql
            BatchUpdateException
            PreparedStatement]))

;; Bind queries to *db* dynamic variable which is bound
;; to each clients database before executing queries
(def ^:dynamic *db* nil)
(def ^:dynamic *db-common* nil)

(defn bool-cols [db-fn params cols]
  (let [row-fn (fn [row]
                 (merge row
                        (map-map val-to-bool (select-keys row cols))))]
    (db-fn *db* params nil {:row-fn row-fn})))

(defn connect!
  [db-config]
  (let [res (map-map
              (fn [pool-spec]
                (assoc pool-spec :db-conn
                                 (delay
                                   (do
                                     (log/info (str "Attaching " (:db-url pool-spec)))
                                     ;;TODO: &serverTimezone=UTC WTF????
                                     (conman/connect! {:jdbc-url (str (:db-url pool-spec) "&serverTimezone=UTC")})))))
              db-config)]
    (alter-var-root (var *db-common*) (constantly @(get-in res [:common :db-conn])))
    res))

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
;; and store connections in db-configs
(defstate db-configs
  :start (connect!
           (database-configs))
  :stop (disconnect! db-configs))

(conman/bind-connection *db* "sql/bass.sql")
(conman/bind-connection *db* "sql/auth.sql")
(conman/bind-connection *db* "sql/messages.sql")
(conman/bind-connection *db* "sql/treatments.sql")
(conman/bind-connection *db* "sql/instruments.sql")
(conman/bind-connection *db* "sql/assessments.sql")
(conman/bind-connection *db* "sql/instrument-answers.sql")
(conman/bind-connection *db-common* "sql/common.sql")

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


;;-------------
;; DB RESOLVING
;;-------------

(defn request-host
  [request]
  (first (filter identity
                 [(get-in request [:headers "x-forwarded-host"])
                  (:server-name request)])))

(defn resolve-db [request]
  (let [db-mappings (env :db-mappings)
        host (keyword (request-host request))
        matching (or (get db-mappings host) (:default db-mappings))]
    (if (contains? db-configs matching)
      (get db-configs matching)
      (throw (Exception. (str "No db present for key " matching " mappings: " db-mappings))))))

;; Why does "HikariDataSource HikariDataSource (HikariPool-XX) has been closed."
;; occur after this file has changed? It seems that mount stops and starts the
;; db-connection AFTER the *db* variable has been bound to a db-connection. This
;; closes the old connection and creates a new one. Which is used at the next
;; request, explaining why it works again then. This should not affect the production
;; environment.
(defn db-middleware
  [handler request]
  (let [db-config (resolve-db request)]
    (request-state/set-state! :name (:name db-config))
    (binding [*db* @(:db-conn db-config)
              bass-locals/*db-config* (cprop.tools/merge-maps bass-locals/db-defaults (filter-map identity db-config))]
      (handler request))))

(defn init-repl
  ([] (init-repl :db1))
  ([db-name]
   (alter-var-root (var *db*) (constantly @(get-in db-configs [db-name :db-conn])))
   (alter-var-root (var locals/*db-config*) (constantly (get db-configs db-name)))
   (alter-var-root (var request-state/*request-state*) (constantly (atom {})))))

;---------------
; SQL WRAPPER
;---------------
(def ^:dynamic *log-queries* false)

(defn sql-wrapper
  [f this db sqlvec options]
  (let [{:keys [val time]} (time+ (apply f [this db sqlvec options]))]
    (request-state/swap-state! :sql-count inc 0)
    (request-state/swap-state! :sql-times #(conj % time) [])
    (when *log-queries*
      (log/info sqlvec)
      (log/info (pr-str val)))
    val))

(defn sql-wrapper-query
  [this db sqlvec options]
  (sql-wrapper hugsql.adapter/query this db sqlvec options))

(defn sql-wrapper-execute
  [this db sqlvec options]
  (sql-wrapper hugsql.adapter/execute this db sqlvec options))

(defmethod hugsql.core/hugsql-command-fn :! [sym] 'bass4.db.core/sql-wrapper-execute)
(defmethod hugsql.core/hugsql-command-fn :execute [sym] 'bass4.db.core/sql-wrapper-execute)
(defmethod hugsql.core/hugsql-command-fn :i! [sym] 'bass4.db.core/sql-wrapper-execute)
(defmethod hugsql.core/hugsql-command-fn :insert [sym] 'bass4.db.core/sql-wrapper-execute)
(defmethod hugsql.core/hugsql-command-fn :<! [sym] 'bass4.db.core/sql-wrapper-query)
(defmethod hugsql.core/hugsql-command-fn :returning-execute [sym] 'bass4.db.core/sql-wrapper-query)
(defmethod hugsql.core/hugsql-command-fn :? [sym] 'bass4.db.core/sql-wrapper-query)
(defmethod hugsql.core/hugsql-command-fn :query [sym] 'bass4.db.core/sql-wrapper-query)
(defmethod hugsql.core/hugsql-command-fn :default [sym] 'bass4.db.core/sql-wrapper-query)
