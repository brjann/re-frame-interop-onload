(ns bass4.external-messages
  (:require [clojure.core.async :refer [go chan <! alt!! >!! timeout dropping-buffer]]
            [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]
            [clj-http.client :as http])
  (:import (java.util.concurrent Executors Executor)))


(defmulti external-message-sender :type)

(defmethod external-message-sender :debug
  [message]
  #_(http/get "https://httpbin.org/delay/0.1"))

(def ^Executor message-thread-pool
  (Executors/newFixedThreadPool 32))

(defn async-error-handler
  [send-fn db-name res]
  (let [exception (:exception res)
        send-res  (try
                    (send-fn
                      "Async error in BASS4"
                      (str "Async error in BASS4: " (.getMessage exception)
                           "\nSee log for details."
                           "\nSent by " db-name))
                    true
                    (catch Exception e
                      e))]
    (log/error "ASYNC ERROR")
    (log/error exception)
    (when-not (true? send-res)
      (log/error "Could not send error email" send-res))))

(defn async-error-chan
  [send-fn db-name]
  (let [err-chan (chan)]
    ;; The go block and err-chan will be GC'd when there is no reference to the message
    ;; https://stackoverflow.com/questions/29879996/how-do-clojure-core-async-channels-get-cleaned-up
    ;; I've confirmed this by storing the err-chan and go in WeakReferences and triggered garbage
    ;; collect. The references disappear.
    (go
      (let [res (<! err-chan)]
        (async-error-handler send-fn db-name res)))
    err-chan))

(defn- dispatch-external-message
  [message]
  (when-not (:error-chan message)
    (throw (ex-info "Sender must provide an error chan" message)))
  (let [err-chan (:error-chan message)
        channels (cond
                   (sequential? (:channels message))
                   (:channels message)

                   (some? (:channels message))
                   [(:channels message)])
        message  (dissoc message :channels)]
    (.execute message-thread-pool
              (bound-fn []
                (log/debug "sending" message)
                (let [res (merge {:message message}
                                 (try
                                   {:result (external-message-sender message)}
                                   (catch Exception e
                                     {:result    :error
                                      :exception e})))]
                  (when channels
                    (doseq [c channels]
                      (let [result (alt!! (timeout 1000) :timeout
                                          [[c res]] :chan)]
                        (when (= :timeout result)
                          (log/debug "Channel timed out")))))
                  (when (= :error (:result res))
                    (>!! err-chan res)))))
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
        (dispatch-external-message {:type       :debug
                                    :channels   channel
                                    :error-chan (chan (dropping-buffer 0))})
        (go
          (<! channel)
          (keep-track))))))