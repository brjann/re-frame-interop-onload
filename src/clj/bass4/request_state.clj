(ns bass4.request-state
  (:require [clojure.tools.logging :as log]
            [bass4.utils :refer [time+] :as utils]))

(def ^:dynamic *request-state*)

(defn swap-state!
  [key f val-if-empty]
  (utils/swap-key! *request-state* key f val-if-empty))

(defn set-state!
  [key val]
  (utils/set-key! *request-state* key val))


(defn get-state
  []
  @*request-state*)
