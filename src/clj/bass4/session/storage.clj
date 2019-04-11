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

(defn read-session-value [datasource table deserialize key]
  (jdbc/with-db-transaction [conn datasource]
    (-> (jdbc/query conn [(str "select value from " (name table) " where session_id = ?") key])
        first :value deserialize)))

(defn update-session-value! [conn table serialize key value]
  (jdbc/with-db-transaction [conn conn]
    (let [data    {:idle_timeout     (:ring.middleware.session-timeout/idle-timeout value)
                   :absolute_timeout (:ring.middleware.session-timeout/absolute-timeout value)
                   :value            (serialize value)}
          updated (jdbc/update! conn table data ["session_id = ? " key])]
      (when (zero? (first updated))
        (jdbc/insert! conn table (assoc data :session_id key)))
      key)))

(defn insert-session-value! [conn table serialize value]
  (let [key (str (UUID/randomUUID))]
    (jdbc/insert!
      conn
      table
      {:session_id       key
       :idle_timeout     (::session-timeout/hard-timeout value)
       :absolute_timeout (:ring.middleware.session-timeout/absolute-timeout value)
       :value            (serialize value)})
    key))

(deftype JdbcStore [datasource table serialize deserialize]
  SessionStore
  (read-session
    [_ key]
    (read-session-value datasource table deserialize key))
  (write-session
    [_ key value]
    (jdbc/with-db-transaction [conn datasource]
      (if key
        (update-session-value! conn table serialize key value)
        (insert-session-value! conn table serialize value))))
  (delete-session
    [_ key]
    (log/debug "Deleting session" key)
    (jdbc/delete! datasource table ["session_id = ?" key])
    nil))

(ns-unmap *ns* '->JdbcStore)

(defn jdbc-store
  ""
  [db-spec & [{:keys [table]
               :or   {table :session_store}}]]
  (let [serialize   serialize-mysql
        deserialize deserialize-mysql]
    (JdbcStore. db-spec table serialize deserialize)))
