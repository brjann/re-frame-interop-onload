(ns bass4.now
  (:require [clj-time.core :as t]))

(defn now
  []
  (t/now))