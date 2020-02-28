(ns bass4.now
  (:require [clj-time.core :as t]))

(defn ^:dynamic now
  []
  (t/now))