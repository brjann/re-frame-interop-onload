(ns bass4.locking)

;;; This atomic map holds all the locks.
;;; It's updated with new locks when they are required.
(def locks (atom {}))

;;; Takes an id which represents the lock and the function
;;; which may only run in one thread at a time for a specific id

(defn lock-transaction
  [lock-id transaction]
  (let [lock-key (keyword (str "lock-id-" lock-id))]
    (compare-and-set! locks (dissoc @locks lock-key) (assoc @locks lock-key lock-id))
    (deref (future
             (locking (lock-key @locks)
               (transaction))))))

;;; Old version that takes an when-done function. Can be used if async is required
#_(defn lock-transaction
    [lock-id transaction when-done]
    (let [lock-key (keyword (str "lock-id-" lock-id))]
      (do
        (compare-and-set! locks (dissoc @locks lock-key) (assoc @locks lock-key lock-id))
        (when-done (deref (future
                            (locking (lock-key @locks)
                              (transaction))))))))

;;; A function to test the locking
(defn test-transaction
  [transaction-count sleep]
  (dotimes [x transaction-count]
    (Thread/sleep sleep)
    (println "performing operation" x)))