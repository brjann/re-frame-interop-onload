(ns bass4.db.core
  (:require
    [clojure.java.jdbc :as jdbc]
    [ring.util.codec :refer [url-encode]]
    [bass4.utils :refer [map-map filter-map time+ val-to-bool]]
    [conman.core :as conman]
    [clojure.set :as set]
    [bass4.config :refer [env]]
    [mount.core :refer [defstate]]
    [bass4.db-config :as db-config]
    [clojure.tools.logging :as log]
    [bass4.request-state :as request-state]
    ;; clj-time.jdbc registers protocol extensions so you don’t have to use clj-time.coerce yourself to coerce to and from SQL timestamps.
    [clj-time.jdbc]
    [bass4.http-utils :as h-utils]
    [metrics.core :as metrics]
    [metrics.reporters.csv :as csv]
    [metrics.reporters.console :as console]
    [bass4.config :as config])
  (:import [java.sql
            BatchUpdateException
            PreparedStatement]
           (java.util Locale)))

;----------------
; SETUP DB STATE
;----------------

(def sql-user-fields
  "ObjectId,
  ObjectId AS `user-id`,
  FirstName AS `first-name`,
  LastName AS `last-name`,
  UserName,
  Email,
  SMSNumber AS `sms-number`,
  DoubleAuthUseBoth AS 'double-auth-use-both?',
  ParentInterface AS `project-id`,
  from_unixtime(LastLogin) AS `last-login-time`,
  `Password`,
  `OldPassword` AS `old-password`,
  CASE
    WHEN (PrivacyNoticeConsentTime IS NULL OR PrivacyNoticeConsentTime = 0)
      THEN NULL
    ELSE from_unixtime(PrivacyNoticeConsentTime)
  END AS `privacy-notice-consent-time`")

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


#_(defstate metrics-reg
    :start (let [metrics-reg (metrics/new-registry)
                 CR          (csv/reporter
                               metrics-reg
                               (str (config/env :bass-path) "/projects/system/bass4-db-log") {:locale (Locale/US)})
                 CR          (console/reporter metrics-reg {})]
             (csv/start CR 10)
             metrics-reg)
    :stop (do))


(defn db-connect!
  [local-config]
  (let [url (db-url local-config (env :database-port))]
    (delay
      (log/info (str "Attaching " (:name local-config)))
      (let [conn (conman/connect! {:jdbc-url          (str url "&serverTimezone=UTC&jdbcCompliantTruncation=false")
                                   :pool-name         (:name local-config)
                                   ;:metric-registry   metrics-reg
                                   :maximum-pool-size 5})]
        (log/info (str (:name local-config) " attached"))
        (jdbc/execute! conn "SET time_zone = '+00:00';")
        conn))))

(defn db-disconnect!
  [db-conn]
  (when (realized? db-conn)
    (log/info (str "Detaching db"))
    (conman/disconnect! @db-conn)))

(defstate db-connections
  :start (map-map db-connect!
                  db-config/local-configs)
  :stop (map-map db-disconnect!
                 db-connections))

(defstate db-common
  :start @(db-connect! db-config/common-config)
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
(conman/bind-connection *db* "sql/registration.sql")
(conman/bind-connection *db* "sql/lost-password.sql")
(conman/bind-connection *db* "sql/privacy.sql")
(conman/bind-connection db-common "sql/common.sql")
(conman/bind-connection db-common "sql/attack-detector.sql")

;---------------------
; COL TRANSFORMATIONS
;---------------------

;; clj-time.jdbc registers protocol extensions,
;; so you don’t have to use clj-time.coerce yourself to coerce to and from SQL timestamps.

(defn bool-cols
  ([db-fn cols] (bool-cols db-fn {} cols))
  ([db-fn params cols]
   (let [row-fn (fn [row]
                  (merge row
                         (map-map val-to-bool (select-keys row cols))))]
     (db-fn *db* params nil {:row-fn row-fn}))))

;---------------
; SQL WRAPPER
;---------------
(def ^:dynamic *log-queries* false)

(defn- bool-cols-row-fn
  []
  (let [bool-keys      (atom nil)
        find-bool-keys (fn [a row]
                         (if (nil? a)
                           (do
                             #_(log/info "Finding bool cols")
                             (->> row
                                  (keys)
                                  (mapv name)
                                  (filter #(re-matches #".*\?$" %))
                                  (mapv keyword)))
                           a))]
    (fn [row]
      (if (map? row)
        (do
          (when (nil? @bool-keys)
            (swap! bool-keys find-bool-keys row))
          #_(log/info @bool-keys)
          (if @bool-keys
            (merge row (map-map val-to-bool (select-keys row @bool-keys)))
            row))
        row))))

(defn sql-wrapper
  [f this db sqlvec options]
  (let [command-options (first (:command-options options))
        row-fn          (if-let [row-fn (:row-fn command-options)]
                          (comp row-fn (bool-cols-row-fn))
                          (bool-cols-row-fn))
        options         (merge options {:command-options
                                        (list (merge command-options
                                                     {:row-fn row-fn}))})
        {:keys [val time]} (time+ (apply f [this db sqlvec options]))]
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

(defn host-db
  [host db-mappings]
  (or (get db-mappings host) (:default db-mappings)))

(defn resolve-db [request]
  (let [db-mappings (env :db-mappings)
        host        (keyword (h-utils/get-server request))
        db-name     (host-db host db-mappings)]
    (if (contains? db-connections db-name)
      [db-name @(get db-connections db-name)]
      (throw (Exception. (str "No db present for host " host " mappings: " db-mappings))))))

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
    (binding [*db*                     db-conn
              db-config/*local-config* (merge db-config/local-defaults (get db-config/local-configs db-name))]
      (handler request))))

(defn init-repl
  ([] (init-repl :db1))
  ([db-name]
   (if (not (contains? db-connections db-name))
     (throw (Exception. (str "db " db-name " does not exists")))
     (do
       (alter-var-root (var host-db) (constantly (constantly db-name)))
       (alter-var-root (var *db*) (constantly @(get db-connections db-name)))
       (alter-var-root (var db-config/*local-config*) (constantly (merge db-config/local-defaults (get db-config/local-configs db-name))))
       (alter-var-root (var request-state/*request-state*) (constantly (atom {})))))))