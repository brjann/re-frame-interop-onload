(ns bass4.db.core
  (:require
    [clojure.java.jdbc :as jdbc]
    [ring.util.codec :refer [url-encode]]
    [bass4.utils :refer [map-map filter-map time+ val-to-bool]]
    [conman.core :as conman]
    [clojure.set :as set]
    [bass4.config :refer [env]]
    [mount.core :refer [defstate]]
    [bass4.bass-locals :as locals]
    [clojure.tools.logging :as log]
    [bass4.request-state :as request-state]
    [bass4.bass-locals :as bass-locals]
    ;; clj-time.jdbc registers protocol extensions so you don’t have to use clj-time.coerce yourself to coerce to and from SQL timestamps.
    [clj-time.jdbc])
  (:import [java.sql
            BatchUpdateException
            PreparedStatement]))

;----------------
; SETUP DB STATE
;----------------

(defn- build-db-url
  [host port name user password]
  (str "jdbc:mysql://" host
       ":" port
       "/" (url-encode name)
       "?user=" (url-encode user)
       "&password=" (url-encode password)))

(defn db-url
  [local-config port]
  (build-db-url
    (:db-host local-config)
    port
    (:db-name local-config)
    (:db-user local-config)
    (:db-password local-config)))

(defn db-connect!
  [local-config]
  (let [url (db-url local-config (env :database-port))]
    (delay
      (log/info (str "Attaching " (:name local-config)))
      (let [conn (conman/connect! {:jdbc-url (str url "&serverTimezone=UTC")})]
        (log/info (str (:name local-config) " attached"))
        conn))))

(defn db-disconnect!
  [db-conn]
  (when (realized? db-conn)
    (log/info (str "Detaching db"))
    (conman/disconnect! @db-conn)))

(defstate db-connections
  :start (map-map db-connect!
                  locals/local-configs)
  :stop (map-map db-disconnect!
                 db-connections))

(defstate db-common
  :start @(db-connect! locals/common-config)
  :stop (do (log/info "Detaching common")
            (conman/disconnect! db-common)))


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
(conman/bind-connection db-common "sql/common.sql")


;---------------------
; COL TRANSFORMATIONS
;---------------------

;; clj-time.jdbc registers protocol extensions so you don’t have to use clj-time.coerce yourself to coerce to and from SQL timestamps.

(defn bool-cols [db-fn params cols]
  (let [row-fn (fn [row]
                 (merge row
                        (map-map val-to-bool (select-keys row cols))))]
    (db-fn *db* params nil {:row-fn row-fn})))

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

;;-------------------------
;; DB RESOLVING MIDDLEWARE
;;-------------------------

(defn request-host
  [request]
  (first (filter identity
                 [(get-in request [:headers "x-forwarded-host"])
                  (:server-name request)])))

(defn host-db
  [host db-mappings]
  (or (get db-mappings host) (:default db-mappings)))

(defn resolve-db [request]
  (let [db-mappings (env :db-mappings)
        host        (keyword (request-host request))
        db-name     (host-db host db-mappings)]
    (if (contains? db-connections db-name)
      [db-name @(get db-connections db-name)]
      (throw (Exception. (str "No db present for key " db-name " mappings: " db-mappings))))))

;; Why does "HikariDataSource HikariDataSource (HikariPool-XX) has been closed."
;; occur after this file has changed? It seems that mount stops and starts the
;; db-connection AFTER the *db* variable has been bound to a db-connection. This
;; closes the old connection and creates a new one. Which is used at the next
;; request, explaining why it works again then. This should not affect the production
;; environment.
(defn db-middleware
  [handler request]
  (let [[db-name db-conn] (resolve-db request)]
    (request-state/set-state! :name (name db-name))
    (binding [*db*                       db-conn
              bass-locals/*local-config* (merge bass-locals/local-defaults (get bass-locals/local-configs db-name))]
      (handler request))))

(defn init-repl
  ([] (init-repl :db1))
  ([db-name]
   (if (not (contains? db-connections db-name))
     (throw (Exception. (str "db " db-name " does not exists")))
     (do
       (alter-var-root (var host-db) (constantly (constantly db-name)))
       (alter-var-root (var *db*) (constantly @(get db-connections db-name)))
       (alter-var-root (var locals/*local-config*) (constantly (merge bass-locals/local-defaults (get bass-locals/local-configs db-name))))
       (alter-var-root (var request-state/*request-state*) (constantly (atom {})))))))