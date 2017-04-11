(ns bass4.services.bass
  (:require [bass4.db.core :as db]
            [bass4.php_clj.core :refer [php->clj]]))

(defn project-title []
  (:title (db/get-project-title)))

#_(defn unserialize-key [m k]
  (merge m {k (into {} (php->clj (get m k)))}))

(defn unserialize-key [m k]
  (->> (get m k)
       (php->clj)
       (#(if (= (class %) flatland.ordered.map.OrderedMap) (into {} %) %))
       (assoc {} k)
       (merge m)))