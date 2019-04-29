(ns bass4.session.cleaner
  "Adapted from https://github.com/luminus-framework/jdbc-ring-session"
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]
            [bass4.db.core :as db])
  (:import [java.util.concurrent Executors TimeUnit ScheduledExecutorService]))

(defn remove-sessions
  "removes stale sessions from the session table"
  [conn {:keys [table]
         :or   {table :session_store}}]
  (let [t (quot (System/currentTimeMillis) 1000)]
    (jdbc/delete! conn table ["hard_timeout < ?" t])))

(defprotocol Stoppable
  "Something that can be stopped"
  (stopped? [_] "Return true if stopped, false otherwise")
  (stop [_] "Stop (idempotent)"))

(defn start-cleaner
  "starts a session cleaner
   conn-state - database connection state
   config - configuration map that ring-jdbc-session was initialized with"
  ([conn-state] (start-cleaner conn-state {}))
  ([conn-state {:keys [interval]
          :or   {interval 60}
          :as   config}]
   (let [scheduler ^ScheduledExecutorService (Executors/newScheduledThreadPool 1)]
     (log/info "Starting cleaner")
     (.scheduleWithFixedDelay scheduler
                              (fn [] (remove-sessions @conn-state config))
                              0
                              (long interval)
                              TimeUnit/SECONDS)

     (reify Stoppable
       (stopped? [_] (.isShutdown scheduler))
       (stop [_] (.shutdown scheduler))))))

;;
;; This was included as a pre-condition
;; #_{:pre [(satisfies? Stoppable session-cleaner)]}
;; but the state does not satisfy Stoppable
;; Unclear why
;;

(defn stop-cleaner
  "stops the instance of the session cleaner"
  [session-cleaner]
  (log/info "Stopping cleaner")
  (.stop session-cleaner))

(defstate
  cleaner
  :start
  (start-cleaner #'db/db-common {:interval 300})

  :stop
  (stop-cleaner cleaner))