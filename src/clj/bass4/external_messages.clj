(ns bass4.external-messages
  (:require [clojure.core.async :refer [go chan <! alt!! timeout]]
            [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]
            [bass4.mailer :as mail])
  (:import (java.util.concurrent Executors Executor)))

(defn- send-email
  [{:keys [to subject message]}]
  (mail/mail! to subject message))

(defn- send-debug-message
  [message])

(def ^Executor executor
  (Executors/newFixedThreadPool 32))

(defn- dispatch-external-message
  [message]
  (.execute executor
            (fn []
              (case (:type message)
                :email
                (send-email message)

                :debug
                (send-debug-message message))
              (when-let [channel (:channel message)]
                (let [result (alt!! (timeout 1000) :timeout
                                    [[channel :success]] :chan)]
                  (when (= :timeout result)
                    (log/debug "Channel timed out")))))))


(defn queue-debug-message!
  [message]
  (log/debug "Received message" message)
  ;; Queue message
  (dispatch-external-message {:type :debug :message message}))

(defn queue-email!
  [to subject message]
  (log/debug "Received message" message)
  ;; Queue message
  (dispatch-external-message {:type    :email
                              :to      to
                              :subject subject
                              :message message}))

(defn queue-counted-debug-message!
  [total-count]
  ;; Queue message
  (let [current-count (atom 0)
        start         (atom nil)
        keep-track    #(let [current-count (swap! current-count inc)]
                         (cond
                           (= 1 current-count)
                           (reset! start (. System (nanoTime)))

                           (= total-count current-count)
                           (log/debug "Sent" current-count "super messages in" (/ (double (- (. System (nanoTime)) @start)) 1000000.0))))]
    (dotimes [_ total-count]
      (let [channel (chan)]
        (dispatch-external-message {:type    :debug
                                    :channel channel})
        (go
          (<! channel)
          (keep-track))))))