(ns bass4.db.sql-wrapper
  (:require [bass4.utils :as utils]
            [clojure.tools.logging :as log]
            [hugsql.core :as hugsql]
            [clojure.java.jdbc :as jdbc]
            [bass4.email :as email]
            [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [bass4.db-config :as db-config]
            [bass4.config :as config]
            [bass4.db.core :as db]
            [bass4.middleware.request-logger :as request-logger])
  (:import (java.sql SQLException)))


(def ^:dynamic *log-queries* false)

(defn- bool-cols-row-fn
  []
  (let [bool-keys      (atom nil)
        find-bool-keys (fn [a row]
                         (if (nil? a)
                           (->> row
                                (keys)
                                (mapv name)
                                (filter #(re-matches #".*\?$" %))
                                (mapv keyword))
                           a))]
    (fn [row]
      (if (map? row)
        (do
          (when (nil? @bool-keys)
            (swap! bool-keys find-bool-keys row))
          (if @bool-keys
            (merge row (utils/map-map utils/val-to-bool (select-keys row @bool-keys)))
            row))
        row))))

(defn- try-query
  ([query n] (try-query query n 1))
  ([query n try#]
   (if (= 1 n)
     (with-meta (query) {:tries try#})
     (try (with-meta (query) {:tries try#})
          (catch SQLException _
            (try-query query (dec n) (inc try#)))))))

(defn sql-wrapper
  [f this db sqlvec options]
  (let [max-tries 3
        query     #(apply f [this db sqlvec options])
        {:keys [val time]} (utils/time+ (try
                                          (try-query query max-tries)
                                          (catch SQLException e
                                            e)))
        error?    (instance? SQLException val)
        unix      (tc/to-epoch (t/now))
        tries     (if error? max-tries (:tries (meta val)))]
    (request-logger/swap-state! :sql-count inc 0)
    (request-logger/swap-state! :sql-times #(conj % time) [])
    (when-not (or (= db db/db-common) config/test-mode?)
      (jdbc/execute! db/db-common [(str "INSERT INTO common_log_queries"
                                        "(`platform`, `db`, `time`, `query`, `duration`, `tries`, `error?`, `error`)"
                                        "VALUES ('clj', ?, ?, ?, ?, ?, ?, ?)")
                                   (db-config/db-name)
                                   unix
                                   (str (first sqlvec))
                                   time
                                   tries
                                   error?
                                   (when error? (.getMessage val))]))
    (when *log-queries*
      (log/info sqlvec)
      (log/info (pr-str val)))
    (when error?
      (throw val))
    (when (< 1 tries)
      (email/async-email!
        db/*db*
        (config/env :error-email)
        "SQL required more than 1 try to succeed"
        (str "DB: " (db-config/db-name) "\n"
             "Time: " unix)))
    val))

(defn sql-wrapper-query
  [this db sqlvec options]
  (let [command-options (first (:command-options options))
        row-fn          (if-let [row-fn (:row-fn command-options)]
                          (comp row-fn (bool-cols-row-fn))
                          (bool-cols-row-fn))
        options         (merge options {:command-options
                                        (list (merge command-options
                                                     {:row-fn row-fn}))})]
    (sql-wrapper hugsql.adapter/query this db sqlvec options)))

(defn sql-wrapper-execute
  [this db sqlvec options]
  (sql-wrapper hugsql.adapter/execute this db sqlvec options))


(defmethod hugsql/hugsql-command-fn :! [_] 'bass4.db.sql-wrapper/sql-wrapper-execute)
(defmethod hugsql/hugsql-command-fn :execute [_] 'bass4.db.sql-wrapper/sql-wrapper-execute)
(defmethod hugsql/hugsql-command-fn :i! [_] 'bass4.db.sql-wrapper/sql-wrapper-execute)
(defmethod hugsql/hugsql-command-fn :insert [_] 'bass4.db.sql-wrapper/sql-wrapper-execute)
(defmethod hugsql/hugsql-command-fn :<! [_] 'bass4.db.sql-wrapper/sql-wrapper-query)
(defmethod hugsql/hugsql-command-fn :returning-execute [_] 'bass4.db.sql-wrapper/sql-wrapper-query)
(defmethod hugsql/hugsql-command-fn :? [_] 'bass4.db.sql-wrapper/sql-wrapper-query)
(defmethod hugsql/hugsql-command-fn :query [_] 'bass4.db.sql-wrapper/sql-wrapper-query)
(defmethod hugsql/hugsql-command-fn :default [_] ''bass4.db.sql-wrapper/sql-wrapper-query)