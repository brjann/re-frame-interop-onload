(ns bass4.clout-cache
  (:require [clout.core :as clout]))

(defn route-matches
  [route request]
  (clout/route-matches route (dissoc request :path-info)))