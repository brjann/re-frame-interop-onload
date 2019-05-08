(ns bass4.db.middleware
  (:require [bass4.middleware.request-logger :as request-logger]
            [bass4.db.core :as db]
            [ring.util.http-response :as http-response]
            [bass4.db-config :as db-config]
            [bass4.http-utils :as h-utils]
            [bass4.config :as config]))


;;-------------------------
;; DB RESOLVING MIDDLEWARE
;;-------------------------

(defn host-db
  [host db-mappings]
  (or (get db-mappings host) (:default db-mappings)))

(defn resolve-db [request]
  (let [db-mappings (config/env :db-mappings)
        host        (keyword (h-utils/get-server request))
        db-name     (host-db host db-mappings)]
    (when (contains? db/db-connections db-name)
      [db-name @(get db/db-connections db-name)]
      #_(throw (Exception. (str "No db present for host " host " mappings: " db-mappings))))))

;; Why does "HikariDataSource HikariDataSource (HikariPool-XX) has been closed."
;; occur after this file has changed? It seems that mount stops and starts the
;; db-connection AFTER the *db* variable has been bound to a db-connection. This
;; closes the old connection and creates a new one. Which is used at the next
;; request, explaining why it works again then. This should not affect the production
;; environment.
(defn db-middleware
  [handler request]
  (if-let [[db-name db-conn] (resolve-db request)]
    (binding [db/*db*                  db-conn
              db-config/*local-config* (merge db-config/local-defaults (get db-config/local-configs db-name))]
      (request-logger/set-state! :name (name db-name))
      (handler request))
    (http-response/not-found "No such DB")))