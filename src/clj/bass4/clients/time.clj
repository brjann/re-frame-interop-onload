(ns bass4.clients.time
  (:require
    [clj-time.core :as t]
    [clj-time.coerce :as tc]
    [bass4.now :as now]
    [bass4.clients.core :as clients]))

(defn day-diff-since
  [today then]
  (let [today-midnight (t/with-time-at-start-of-day today)
        then-midnight  (t/with-time-at-start-of-day then)]
    (if (t/after? today-midnight then-midnight)
      (t/in-days
        (t/interval then-midnight today-midnight))
      (- (t/in-days
           (t/interval today-midnight then-midnight))))))

(defn day-diff-since-tz
  [today then]
  (let [tz (t/time-zone-for-id (clients/client-setting [:timezone]))]
    (day-diff-since
      (t/to-time-zone today tz)
      (t/to-time-zone then tz))))

(defn days-since
  [then time-zone]
  (day-diff-since
    (t/to-time-zone (now/now) time-zone)
    (t/to-time-zone then time-zone)))

(defn days-since-tz
  [then]
  (days-since then (t/time-zone-for-id (clients/client-setting [:timezone]))))

(defn local-midnight
  ([] (local-midnight (now/now)))
  ([date-time]
   (local-midnight date-time (t/time-zone-for-id (clients/client-setting [:timezone]))))
  ([date-time time-zone]
   (t/with-time-at-start-of-day (t/to-time-zone date-time time-zone))))