(ns bass4.services.bass
  (:require [bass4.db.core :as db]
            [bass4.php_clj.core :refer [php->clj]]))

(defn project-title []
  (:title (db/get-project-title)))

#_(defn unserialize-key [m k]
  (merge m {k (into {} (php->clj (get m k)))}))

(defn unserialize-key
  ([m k] (unserialize-key m k identity))
  ([m k f]
    (->> (get m k)
         (php->clj)
         (#(if (= (class %) flatland.ordered.map.OrderedMap) (into {} %) %))
         f
         (assoc {} k)
         (merge m))))

(defn map-map [f m]
  (let [ks (keys m)
        vs (vals m)]
    (zipmap ks (mapv f vs))))