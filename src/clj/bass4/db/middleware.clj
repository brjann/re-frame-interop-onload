(ns bass4.db.middleware
  (:require [bass4.middleware.request-logger :as request-logger]
            [bass4.db.core :as db]
            [ring.util.http-response :as http-response]
            [bass4.http-utils :as h-utils]
            [bass4.config :as config]
            [bass4.clients :as clients]))


;;-------------------------
;; DB RESOLVING MIDDLEWARE
;;-------------------------

(defn resolve-db [request]
  (let [host    (h-utils/get-server request)
        name-id (some (fn [[k v]]
                        (when (= host (:bass4-host v))
                          k))
                      clients/local-configs)]
    (when (contains? db/db-connections name-id)
      [name-id @(get db/db-connections name-id)]
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
    (binding [db/*db*                db-conn
              clients/*local-config* (get clients/local-configs db-name)]
      (request-logger/set-state! :name (name db-name))
      (handler request))
    (->
      (http-response/not-found "No such DB")
      (http-response/content-type "text/plain; charset=utf-8"))))