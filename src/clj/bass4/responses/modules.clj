(ns bass4.responses.modules
  (:require [bass4.services.user :as user]
            [ring.util.http-response :as response]
            [schema.core :as s]
            [bass4.layout :as layout]
            [clojure.tools.logging :as log]))



(defn modules-list [modules]
  ["modules-list.html"
   {:modules modules}])

(defn module [module-id modules]
  (let [module (->> (filter #(= module-id (:module-id %)) modules)
                    (some #(and (:active %) %)))]
    ["module.html"
     {:module module}]))