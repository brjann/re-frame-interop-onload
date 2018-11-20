(ns bass4.db.sql-wrapper
  (:require [bass4.utils :as utils]
            [bass4.request-state :as request-state]
            [clojure.tools.logging :as log]
            [hugsql.core :as hugsql]))


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

(defn sql-wrapper
  [f this db sqlvec options]
  (let [{:keys [val time]} (utils/time+ (apply f [this db sqlvec options]))]
    (request-state/swap-state! :sql-count inc 0)
    (request-state/swap-state! :sql-times #(conj % time) [])
    (when *log-queries*
      (log/info sqlvec)
      (log/info (pr-str val)))
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