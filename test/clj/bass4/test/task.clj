(ns bass4.test.task
  (:require [clojure.test :refer :all]
            [clojure.core.async :as a :refer [chan put! timeout alts!! dropping-buffer go <! close!]]
            [bass4.test.core :refer :all]
            [bass4.task.adder :as task-adder]
            [bass4.task.scheduler :as scheduler]
            [clojure.tools.logging :as log]
            [bass4.task.log :as task-log]))

(use-fixtures
  :once
  test-fixtures)

(use-fixtures
  :each
  (fn [f]
    (binding [task-log/*log?* false]
      (f))))

(defn task-fn
  [c v]
  (let [runs (atom 0)]
    (fn [& _]
      (if (= 3 (swap! runs inc))
        (close! c)
        (put! c v))
      {})))

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
    (task-adder/add-by-millisecond-task! task1 10)
    (task-adder/add-by-millisecond-task! task2 10)
    (task-adder/add-by-millisecond-task! task3 10)
    (task-adder/add-by-millisecond-task! task4 10)
    (task-adder/add-by-millisecond-task! task5 10)
    (task-adder/add-by-millisecond-task! task6 10)
    (let [c (a/map (fn [& more] more) [c1 c2 c3 c4 c5 c6])]
      (is (= [1 2 3 4 5 6] (first (alts!! [c (timeout 5000)]))))
      (is (= [1 2 3 4 5 6] (first (alts!! [c (timeout 5000)]))))
      (is (nil? (first (alts!! [c (timeout 5000)])))))
    (scheduler/cancel-all!)))

