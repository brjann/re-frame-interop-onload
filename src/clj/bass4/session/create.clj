(ns bass4.session.create
  (:require [clj-time.core :as t]
            [clojure.tools.logging :as log]
            [bass4.now :as now]
            [bass4.session.timeout :as session-timeout]))

(defn new
  [user additional]
  (merge
    {:user-id         (:user-id user)
     :auth-re-auth?   nil
     :last-login-time (:last-login-time user)
     :session-start   (now/now)}
    (when-not (:external-login? additional)
      (session-timeout/re-auth-timeout-map))
    (session-timeout/hard-timeout-map (:external-login? additional))
    additional))

(defn assoc-out-session
  [response session-in merge-map]
  (let [session-out      (:session response)
        session-deleted? (and (contains? response :session) (empty? session-out))]
    (if session-deleted?
      response
      (assoc response :session (merge (or session-out
                                          session-in)
                                      merge-map)))))