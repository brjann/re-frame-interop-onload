(ns bass4.external-messages
  (:require [clojure.core.async :refer [go chan <! alt!! >!! timeout]]
            [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]
            [clj-http.client :as http])
  (:import (java.util.concurrent Executors Executor)))


(defmulti send-external-message :type)

(defmethod send-external-message :debug
  [message]
  #_(http/get "https://httpbin.org/delay/0.1"))

(def ^Executor message-thread-pool
  (Executors/newFixedThreadPool 32))

(defn- dispatch-external-message
  [message]
  (let [err-chan (chan)]
    ;; This go block will be GC'd when the
    ;; https://stackoverflow.com/questions/29879996/how-do-clojure-core-async-channels-get-cleaned-up
    (go
      (let [error (<! err-chan)]
        (throw (ex-info "Unhandled message error" error))))
    (.execute message-thread-pool
              (fn []
                (let [channels (cond
                                 (sequential? (:channels message))
                                 (:channels message)

                                 (some? (:channels message))
                                 [(:channels message)])
                      message  (dissoc message :channels)
                      res      (merge {:message message}
                                      (try
                                        {:result (send-external-message message)}
                                        (catch Exception e
                                          {:result    :error
                                           :exception e})))]
                  (if channels
                    (doseq [c channels]
                      (let [result (alt!! (timeout 1000) :timeout
                                          [[c res]] :chan)]
                        (when (= :timeout result)
                          (log/debug "Channel timed out"))))
                    (when (= :error (:result res))
                      (>!! err-chan res))))))
    nil))


(def ^:dynamic *debug-chan* nil)

(defn queue-message!
  [message]
  (let [channels (if *debug-chan*
                   (conj (:channels message) *debug-chan*)
                   (:channels message))]
    (dispatch-external-message (merge message {:channels channels}))))

(defn queue-message-c!
  [message]
  (let [c (chan)]
    (queue-message! (merge
                      message
                      {:channels (conj (:channels message) c)}))
    c))

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
        (dispatch-external-message {:type     :debug
                                    :channels channel})
        (go
          (<! channel)
          (keep-track))))))