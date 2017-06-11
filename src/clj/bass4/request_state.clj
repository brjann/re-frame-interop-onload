(ns bass4.request-state
  (:require [clojure.tools.logging :as log]
            [bass4.utils :as utils]))

(def ^:dynamic *request-state*)

#_(defn request-state-wrapper
  [handler]
  (fn [request]
    (binding [*request-state* (atom {})]
      (let [res (handler request)]
        (log/debug "hejsan hoppsan")
        (log/debug *request-state*)
        res))))

(defn swap-state!
  [key f val-if-empty]
  (utils/swap-key! *request-state* key f val-if-empty))

(defn request-state-wrapper
  [handler request]
  (binding [*request-state* (atom {})]
    (let [res (handler request)]
      #_(log/debug *request-state*)
      res)))