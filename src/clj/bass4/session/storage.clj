(ns bass4.session.storage
  "Adapted from https://github.com/luminus-framework/jdbc-ring-session
  All db adapters except mysql removed"
  (:require [clojure.java.jdbc :as jdbc]
            [taoensso.nippy :as nippy]
            [ring.middleware.session.store :refer :all]
            [bass4.session.timeout :as session-timeout]
            [clojure.tools.logging :as log])
  (:import java.util.UUID))

(defn serialize-mysql [value]
  (nippy/freeze value))

(defn deserialize-mysql [value]
  (when value
    (nippy/thaw value)))

(defn read-session-value [datasource table key]
  (jdbc/with-db-transaction [conn datasource]
    (-> (jdbc/query conn [(str "select value from " (name table) " where session_id = ?") key])
        first
        :value
        deserialize-mysql)))

(defn update-session-value! [conn table key value]
  (jdbc/with-db-transaction [conn conn]
    (let [data    {:hard_timeout (::session-timeout/hard-timeout-at value)
                   :value        (serialize-mysql value)}
          updated (jdbc/update! conn table data ["session_id = ? " key])]
      (when (zero? (first updated))
        (jdbc/insert! conn table (assoc data :session_id key)))
      key)))

(defn insert-session-value! [conn table value]
  (let [key (str (UUID/randomUUID))]
    (jdbc/insert!
      conn
      table
      {:session_id   key
       :hard_timeout (::session-timeout/hard-timeout-at value)
       :value        (serialize-mysql value)})
    key))

(deftype JdbcStore [datasource-state table]
  SessionStore
  (read-session
    [_ key]
    (read-session-value @datasource-state table key))
  (write-session
    [_ key value]
    (jdbc/with-db-transaction [conn @datasource-state]
      (if key
        (update-session-value! conn table key value)
        (insert-session-value! conn table value))))
  (delete-session
    [_ key]
    (jdbc/delete! @datasource-state table ["session_id = ?" key])
    nil))

(ns-unmap *ns* '->JdbcStore)

(defn jdbc-store
  ""
  [db-spec & [{:keys [table]
               :or   {table :session_store}}]]
  (JdbcStore. db-spec table))
