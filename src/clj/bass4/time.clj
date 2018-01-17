(ns bass4.time
  (:require
    [clj-time.core :as t]
    [clj-time.coerce :as tc]))

(defn day-diff-since
  [today then]
  (let [today-midnight (t/with-time-at-start-of-day today)
        then-midnight  (t/with-time-at-start-of-day then)]
    (if (t/after? today-midnight then-midnight)
      (t/in-days
        (t/interval then-midnight today-midnight))
      (- (t/in-days
           (t/interval today-midnight then-midnight))))))

(defn days-since
  [then time-zone]
  (day-diff-since
    (t/from-time-zone (t/now) time-zone)
    (t/from-time-zone then time-zone)))


(defn from-unix
  [timestamp]
  (tc/from-long (* 1000 timestamp)))
#_(defn day-diff-since
    [today then]
    (t/day then))