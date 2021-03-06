(ns bass4.db.core
  (:require
    [ring.util.codec :refer [url-encode]]
    [conman.core :as conman]
    [mount.core :refer [defstate]]
    [bass4.db-common :as db-common]
    [clojure.tools.logging :as log]
    ;; clj-time.jdbc registers protocol extensions so you don’t have to use clj-time.coerce yourself to coerce to and from SQL timestamps.
    [clj-time.jdbc]
    [bass4.config :as config]
    [clojure.java.jdbc :as jdbc]))

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
  `DisablePluggableUI` as `disable-pluggable-ui?`,
  Personnummer as `pid-number`,
  `Group` AS `group`,
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

(defn jdbc-map
  [local-config]
  {:jdbc-url            (str (db-url local-config (config/env :database-port))
                             "&serverTimezone=UTC"
                             "&jdbcCompliantTruncation=false"
                             "&useUnicode=true"
                             "&characterEncoding=utf8"
                             "&useSSL=false")
   :pool-name           (:name local-config)
   ;:metric-registry   metrics-reg
   :maximum-pool-size   5
   :connection-init-sql "SET time_zone = '+00:00';"})

(defn db-connect!
  ;; NOTE: MySQL variables cannot be executed on conn
  ;; since multiple connection are made for each database.
  ;; And :connection-init-sql only accepts one command.
  ;; Proper SQL mode (e.g. MYSQL40) must therefore be set in my.cnf
  [local-config]
  (log/info (str "Attaching " (:name local-config)))
  (let [conn (conman/connect! (jdbc-map local-config))]
    (log/info (str (:name local-config) " attached"))
    conn))

(defn db-disconnect!
  [db-conn name]
  ;; The connection is always realized but for compatibility
  ;; reasons it is hidden behind a delay
  (log/info (str "Detaching db " name))
  (conman/disconnect! @db-conn))

(defstate db-common
  :start (db-connect! db-common/common-config)
  :stop (do (log/info "Detaching common")
            (conman/disconnect! db-common)))

(defn load-client-db-configs
  [db-common]
  (jdbc/query db-common ["SELECT * from client_dbs"]))

;; Bind queries to *db* dynamic variable which is bound
;; to each clients database before executing queries
(defonce ^:dynamic *db* nil)

(conman/bind-connection *db*
                        "sql/bass.sql"
                        "sql/auth.sql"
                        "sql/messages.sql"
                        "sql/treatments.sql"
                        "sql/instruments.sql"
                        "sql/assessments.sql"
                        "sql/assessment-reminder.sql"
                        "sql/assessment-flagger.sql"
                        "sql/instrument-answers.sql"
                        "sql/registration.sql"
                        "sql/lost-password.sql"
                        "sql/privacy.sql"
                        "sql/external-messages.sql"
                        "sql/admin-reminder.sql")

(conman/bind-connection db-common
                        "sql/common.sql"
                        "sql/attack-detector.sql")

#_(defstate metrics-reg
    :start (let [metrics-reg (metrics/new-registry)
                 CR          (csv/reporter
                               metrics-reg
                               (str (config/env :bass-path) "/projects/system/bass4-db-log") {:locale (Locale/US)})
                 report-freq (env :metrics-report-freq 60)]
             (csv/start CR report-freq)
             metrics-reg)
    :stop (do))
