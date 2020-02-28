(ns ^:eftest/synchronized
  bass4.test.task
  (:require [clojure.test :refer :all]
            [clojure.core.async :as a :refer [chan put! timeout alts!! dropping-buffer go <! close!]]
            [bass4.test.core :refer :all]
            [bass4.task.scheduler :as scheduler]
            [bass4.task.log :as task-log]
            [bass4.task.scheduler :as task-scheduler]))

(use-fixtures
  :once
  test-fixtures)

(use-fixtures
  :once
  (fn [f]
    (binding [task-log/*log?* false]
      (f))))

(defn task-fn
  [c v]
  (fn [& _]
    (put! c v)
    {}))

;; FAILS IN BAT-TEST, PROBABLY BECAUSE defstate IS NOT USED

(deftest schedule-many
  (let [n  33
        cs (repeatedly n #(chan 2))
        fs (for [i (range n)]
             (task-fn (nth cs i) i))]
    (doseq [f fs]
      (task-scheduler/schedule-db-task! f ::task-scheduler/by-millisecond 10))
    (let [c  (a/map (fn [& more] more) cs)
          r1 (alts!! [c (timeout 5000)])
          r2 (alts!! [c (timeout 5000)])]
      (is (= c (second r1)))
      (is (= c (second r2)))
      (is (= (range n) (first r1)))
      (is (= (range n) (first r2))))
    (scheduler/cancel-all!)))