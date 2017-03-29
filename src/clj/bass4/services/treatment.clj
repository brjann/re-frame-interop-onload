(ns bass4.services.treatment
  (:require [bass4.db.core :as db]
            [bass4.php_clj.core :refer [php->clj]]))

(defn unserialize-key [m k]
  (merge m {k (into {} (php->clj (get m k)))}))

;; TODO: Does not check if treatment is ongoing or other options (disallow send etc)
(defn user-treatments [user-id]
  (let [treatments (mapv #(unserialize-key % :module-accesses)
                         (db/get-linked-treatments {:user-id user-id}))]
    treatments))

;; (alter-var-root (var bass4.db.core/*db*) (fn [x] (:db1 bass4.db.core/*dbs*)))

(defn get-treatment-modules [treatment]
  (let [treatment-id (:treatment-id treatment)]
    (db/get-treatment-modules {:treatment-id treatment-id})))

(defn get-active-module-ids [treatment]
  (->> treatment
       :module-accesses
       (filterv (fn [[k v]] (not= 0 v)))
       (mapv first)))

(defn tag-active-modules [treatment-modules active-module-ids]
  (map #(if (some #{(:module-id %)} active-module-ids)
          (assoc % :active true)
          (assoc % :active false))
       treatment-modules))

(defn user-treatment-info [user-id]
  (let [treatments (user-treatments user-id)
        treatment-modules (mapv get-treatment-modules treatments)
        active-module-ids (mapv get-active-module-ids treatments)
        treatment-modules (mapv tag-active-modules treatment-modules active-module-ids)
        treatments (mapv #(assoc %1 :modules %2) treatments treatment-modules)]
    treatments))


#_(defn get-active-worksheets [treatment]
  (let [active-ids (get-active-module-ids treatment)]
    (db/get-active-worksheets {:module-ids active-ids})))