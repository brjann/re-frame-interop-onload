(ns bass4.services.treatment
  (:require [bass4.db.core :as db]
            [bass4.php_clj.core :refer [php->clj]]
            [bass4.utils :refer [unserialize-key map-map]]))

;; TODO: Does not check if treatment is ongoing or other options (disallow send etc)
;; TODO: Merge info from several ongoing treatments

(defn val-to-int [m k]
  (merge m {k (into {} (map-map #(if (integer? %) % (Integer/parseInt %))
                                (get m k)))}))

(defn user-treatments [user-id]
  (let [treatments (mapv #(-> %
                              (unserialize-key :module-accesses)
                              (val-to-int :module-accesses))
                         (db/get-linked-treatments {:user-id user-id}))]
    treatments))


(defn add-treatment-modules [treatment-access]
  (assoc treatment-access :modules (db/get-treatment-modules {:treatment-id (:treatment-id treatment-access)})))

(defn tag-active-modules [treatment-access]
  (let [active-modules-ids
        (->> treatment-access
             :module-accesses
             (filterv (fn [[k v]] (not= 0 v)))
             (mapv first))
        tagged (mapv #(if (some #{(:module-id %)} active-modules-ids)
                         (assoc % :active true)
                         (assoc % :active false))
                      (:modules treatment-access))]
    (assoc treatment-access :modules tagged :active-module-ids active-modules-ids)))


(defn add-active-worksheets [treatment-access]
    (assoc treatment-access :worksheets (db/get-module-worksheets {:module-ids (:active-module-ids treatment-access)})))

(defn user-treatment-info [user-id]
  (let [tx-info (mapv #(-> %
                     add-treatment-modules
                     tag-active-modules
                     add-active-worksheets) (user-treatments user-id))]
    tx-info))

