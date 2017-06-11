(ns bass4.request-state
  (:require [clojure.tools.logging :as log]))

(def ^:dynamic *request-state*)

#_(defn request-state-wrapper
  [handler]
  (fn [request]
    (binding [*request-state* (atom {})]
      (let [res (handler request)]
        (log/debug "hejsan hoppsan")
        (log/debug *request-state*)
        res))))

(defn request-state-wrapper
  [handler request]
  (binding [*request-state* (atom {})]
    (let [res (handler request)]
      (log/debug *request-state*)
      res)))