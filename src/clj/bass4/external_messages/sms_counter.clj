(ns bass4.external-messages.sms-counter
  (:require [bass4.utils :as utils]
            [bass4.now :as now])
  (:refer-clojure :exclude [count]))

(def ^:dynamic counter (atom []))

(def window (* 24 60 60))

(defn inc!
  []
  (let [time (utils/to-unix (now/now))]
    (swap! counter conj time)
    nil))

(defn count
  []
  (let [time-window (- (utils/to-unix (now/now))
                       window)]
    (swap! counter (fn [a] (filter #(< time-window %) a)))
    (clojure.core/count @counter)))