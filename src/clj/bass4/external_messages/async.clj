(ns bass4.external-messages.async
  (:require [clojure.core.async :refer [go chan <! alt!! >!! timeout dropping-buffer put!]]
            [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]
            [clj-http.client :as http])
  (:import (java.util.concurrent Executors Executor)))


(defmulti async-message-sender :type)

(defmethod async-message-sender :debug
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
        message  (dissoc message :channels :error-chan)]
    (.execute message-thread-pool
              (bound-fn []
                (let [res (merge {:message message}
                                 (try
                                   {:result (async-message-sender message)}
                                   (catch Exception e
                                     {:result    :exception
                                      :exception e})))]
                  (when channels
                    (doseq [c channels]
                      (put! c res)))
                  (when (= :exception (:result res))
                    (>!! err-chan res)))))
    nil))


(def ^:dynamic *debug-chan* nil)

(defn queue-message!
  [message]
  (let [out-chan (chan)
        channels (conj (:channels message) out-chan)
        channels (if *debug-chan*
                   (conj channels *debug-chan*)
                   channels)]
    (dispatch-external-message (merge message {:channels channels}))
    out-chan))

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
                           (log/info "Sent" current-count "super messages in" (/ (double (- (. System (nanoTime)) @start)) 1000000.0))))]
    (dotimes [_ total-count]
      (let [channel (chan)]
        (dispatch-external-message {:type       :debug
                                    :channels   channel
                                    :error-chan (chan (dropping-buffer 0))})
        (go
          (<! channel)
          (keep-track))))))
