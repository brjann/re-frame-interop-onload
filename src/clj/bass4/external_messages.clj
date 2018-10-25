(ns bass4.external-messages
  (:require [clojure.core.async :refer [thread go chan dropping-buffer <! >!! <!! alts!]]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [bass4.utils :as utils]
            [mount.core :refer [defstate]])
  (:import (clojure.lang PersistentQueue)
           (java.util UUID)))



(def message-queue (atom PersistentQueue/EMPTY))

;; -----------------
;;  DEBUG VARIABLES
;; -----------------
;; To stop go loop from REPL
(def thread-manager-running? (atom true))
;; To keep track of manager instances
(def manager-count (atom 0))
;; To keep track of thread instances
(def thread-count (atom 0))


;; ---------------------------
;;  WATCHES FOR SANITY CHECKS
;; ---------------------------
(add-watch thread-count :_ (fn [_ _ _ new-count]
                             (when (< 1 new-count)
                               (throw (Exception. (str "Message thread count is > 1! Count: " new-count))))))
(add-watch manager-count :_ (fn [_ _ _ new-count]
                              (when (< 1 new-count)
                                (throw (Exception. (str "Thread manager count is > 1! Count: " new-count))))))


;; ----------------------------------
;;  SENDER THREAD AND THREAD MANAGER
;; ----------------------------------
;; Used to communicate status of queue thread
(def sender-thread-running? (agent false))

(defn- start-sender-thread! []
  (swap! thread-count inc)
  (thread
    (let [last-message (atom (t/now))
          continue?    (atom true)]

      (while @continue?
        ;; If queue-running? is checked by the manager here,
        ;; it will be true, meaning that at least one more
        ;; cycle will be completed -> all queued messages will be sent

        ;; Run message cycle. It will return current state
        (send-off sender-thread-running?
                  (fn [_]
                    ;; If there is a message - send it
                    (when-let [message (utils/queue-pop! message-queue)]
                      (log/debug "Sending message" message "...")
                      ;; Sleep .5 secs to simulate IO.
                      (Thread/sleep 500)
                      (log/debug "Message" message "sent")
                      ;; Update last message send time to now
                      (swap! last-message (constantly (t/now))))

                    ;; Return value of agent is true if less than 5 seconds
                    ;; have elapsed since last message send
                    (> 5 (t/in-seconds (t/interval @last-message (t/now))))))

        ;; Wait until message cycle has been completed
        ;; and continue status has been determined
        (await sender-thread-running?)

        ;; This is the only place within the loop where
        ;; @queue-running? can be false (loop will end)
        (swap! continue? (constantly @sender-thread-running?))))
    (swap! thread-count dec)
    (log/debug "Exiting send thread")))

;; Channel used to communicate to thread manager that a
;; message has been added to queue
(log/debug "NEW CHANNEL!")
(def message-notification (chan (dropping-buffer 1)))

(defn- start-thread-manager
  [stop-chan]
  (swap! thread-manager-running? (constantly true))
  (swap! manager-count inc)
  (go
    (log/debug "Starting manager")
    (while @thread-manager-running?

      ;; Wait for a signal that message has been added
      ;; to queue.
      (let [[notification _] (alts! [message-notification stop-chan])]
        (log/debug "Notification" notification)
        (when (= :stop notification)
          (swap! thread-manager-running? (constantly false)))
        (log/debug @thread-manager-running?)
        (when @thread-manager-running?
          (log/debug "Got signal - message has been queued")

          ;; Wait while queue completes current cycle
          (await sender-thread-running?)
          (log/debug "Message cycle has been completed")

          ;; If @queue-running? is true, we know that at least
          ;; one more cycle will be completed. Since the message
          ;; is in the queue (or has already been sent),
          ;; it is bound to be sent by the current running thread

          ;; If @queue-agent is false, the thread will not
          ;; run anymore cycles. However, the message may already
          ;; have been sent. Therefore check the queue before
          ;; creating a new thread.
          (when (and (not @sender-thread-running?) (not-empty @message-queue))
            (send sender-thread-running? (constantly true))
            (log/debug "Starting send thread")
            (start-sender-thread!)))))
    (swap! manager-count dec)
    (log/debug "Exiting manager")))

(defn queue-message!
  [message]
  (log/debug "Received message" message)
  ;; Queue message
  (utils/queue-add! message-queue message)

  ;; Notify manager that message has been added to queue.
  ;; The dropping buffer of 1 makes sure that all puts succeed
  (>!! message-notification true))

(defstate external-messages-queue
  :start (let [c (chan (dropping-buffer 1))]
           (log/debug "Starting!")
           (start-thread-manager c)
           c)
  :stop (do
          (log/debug "Stopping!" external-messages-queue)
          (>!! external-messages-queue :stop)
          #_(swap! thread-manager-running? (constantly false))
          #_(log/debug "Running?" @thread-manager-running?)
          #_(>!! message-notification true)))

(def x 8)

;; Usage
#_(comment
    (do
      (queue-message! :one)
      (queue-message! :two)
      (Thread/sleep 4000)
      ;; Sent in same thread
      (queue-message! :three)
      (Thread/sleep 5600)
      ;; Slept for > 5 secs, new thread is created
      (queue-message! :four)
      (swap! thread-manager-running? (constantly false))
      ;; Send one more message to let manager complete cycle and exit
      (queue-message! :last)))