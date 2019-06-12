(ns bass4.test.task
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

(deftest schedule-many
  (let [c1    (chan 2)
        c2    (chan 2)
        c3    (chan 2)
        c4    (chan 2)
        c5    (chan 2)
        c6    (chan 2)
        task1 (task-fn c1 1)
        task2 (task-fn c2 2)
        task3 (task-fn c3 3)
        task4 (task-fn c4 4)
        task5 (task-fn c5 5)
        task6 (task-fn c6 6)]
    (task-scheduler/schedule! task1 ::task-scheduler/by-millisecond 10)
    (task-scheduler/schedule! task2 ::task-scheduler/by-millisecond 10)
    (task-scheduler/schedule! task3 ::task-scheduler/by-millisecond 10)
    (task-scheduler/schedule! task4 ::task-scheduler/by-millisecond 10)
    (task-scheduler/schedule! task5 ::task-scheduler/by-millisecond 10)
    (task-scheduler/schedule! task6 ::task-scheduler/by-millisecond 10)
    (let [c  (a/map (fn [& more] more) [c1 c2 c3 c4 c5 c6])
          r1 (alts!! [c (timeout 5000)])
          r2 (alts!! [c (timeout 5000)])]
      (is (= c (second r1)))
      (is (= c (second r2)))
      (is (= [1 2 3 4 5 6] (first r1)))
      (is (= [1 2 3 4 5 6] (first r2))))
    (scheduler/cancel-all!)))

