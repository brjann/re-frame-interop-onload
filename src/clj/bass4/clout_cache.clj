(ns bass4.clout-cache
  (:require [clout.core :as clout]
            [clojure.tools.logging :as log]))

(def compiled-routes (atom {}))

(defn route-matches
  [route request]
  (let [c-route (if (contains? @compiled-routes route)
                  (get @compiled-routes route)
                  (let [c-route (clout/route-compile route)]
                    (swap! compiled-routes assoc route c-route)
                    c-route))]
    (clout/route-matches c-route (dissoc request :path-info))))