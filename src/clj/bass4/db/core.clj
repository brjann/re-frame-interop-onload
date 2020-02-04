(ns bass4.db.core
  (:require
    [ring.util.codec :refer [url-encode]]
    [bass4.utils :refer [map-map filter-map time+ val-to-bool]]
    [conman.core :as conman]
    [mount.core :refer [defstate]]
    [bass4.db-config :as db-config]
    [clojure.tools.logging :as log]
    ;; clj-time.jdbc registers protocol extensions so you don’t have to use clj-time.coerce yourself to coerce to and from SQL timestamps.
    [clj-time.jdbc]
    [bass4.config :as config]))

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


#_(defstate metrics-reg
    :start (let [metrics-reg (metrics/new-registry)
                 CR          (csv/reporter
                               metrics-reg
                               (str (config/env :bass-path) "/projects/system/bass4-db-log") {:locale (Locale/US)})
                 report-freq (env :metrics-report-freq 60)]
             (csv/start CR report-freq)
             metrics-reg)
    :stop (do))


(defn db-connect!
  ;; NOTE: MySQL variables cannot be executed on conn
  ;; since multiple connection are made for each database.
  ;; And :connection-init-sql only accepts one command.
  ;; Proper SQL mode (e.g. MYSQL40) must therefore be set in my.cnf
  [local-config]
  (let [url (db-url local-config (config/env :database-port))]
    (delay
      (log/info (str "Attaching " (:name local-config)))
      (let [conn (conman/connect! {:jdbc-url            (str url
                                                             "&serverTimezone=UTC"
                                                             "&jdbcCompliantTruncation=false"
                                                             "&useUnicode=true"
                                                             "&characterEncoding=utf8"
                                                             "&useSSL=false")
                                   :pool-name           (:name local-config)
                                   ;:metric-registry   metrics-reg
                                   :maximum-pool-size   5
                                   :connection-init-sql "SET time_zone = '+00:00';"})]
        (log/info (str (:name local-config) " attached"))
        conn))))

(defn db-disconnect!
  [db-conn]
  (when (realized? db-conn)
    (log/info (str "Detaching db"))
    (conman/disconnect! @db-conn)))


;; Bind queries to *db* dynamic variable which is bound
;; to each clients database before executing queries
(def ^:dynamic *db* nil)

(defonce connected-dbs (atom {}))

(defstate db-connections
  :start (let [x (map-map db-connect!
                          db-config/local-configs)]
           (when (config/env :dev)
             (log/info "Setting *db* to dev database")
             (def ^:dynamic *db* @(get x (config/env :dev-db))))
           (reset! connected-dbs x)
           x)
  :stop (map-map db-disconnect!
                 db-connections))

(defstate db-common
  :start @(db-connect! db-config/common-config)
  :stop (do (log/info "Detaching common")
            (conman/disconnect! db-common)))

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
                        "sql/external-messages.sql")

(conman/bind-connection db-common
                        "sql/common.sql"
                        "sql/attack-detector.sql")

;; clj-time.jdbc registers protocol extensions,
;; so you don’t have to use clj-time.coerce yourself to coerce to and from SQL timestamps.