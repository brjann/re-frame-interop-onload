(ns bass4.services.treatment
  (:require [bass4.db.core :as db]
            [bass4.php_clj.core :refer [php->clj]]
            [bass4.utils :refer [unserialize-key map-map]]))

;; TODO: Does not check if treatment is ongoing or other options (disallow send etc)
;; TODO: Merge info from several ongoing treatments
;; TODO: Does probably not handle automatic module accesses

#_(defn val-to-int [m k]
    (merge m {k (into {} (map-map #(if (integer? %) % (Integer/parseInt %))
                                  (get m k)))}))

;; https://github.com/clojure/clojure/blob/clojure-1.9.0-alpha14/src/clj/clojure/core.clj#L519
(defn boolean?
  "Return true if x is a Boolean"
  {:added "1.9"}
  [x] (instance? Boolean x))


(defn val-to-int [m k]
  (merge m {k (into {} (map-map #(cond
                                   (integer? %) %
                                   (boolean? %) (if % 1 0)
                                   :else
                                   (Integer/parseInt %))
                                (get m k)))}))

(defn- user-treatments [user-id]
  (let [treatments (mapv #(-> %
                              (unserialize-key :module-accesses)
                              (val-to-int :module-accesses))
                         (db/get-linked-treatments {:user-id user-id}))]
    treatments))


(defn- add-treatment-modules [treatment-access]
  (assoc treatment-access :modules (db/get-treatment-modules {:treatment-id (:treatment-id treatment-access)})))

(defn- active-modules
  [treatment-access]
  (->> treatment-access
       :module-accesses
       (filterv (fn [[k v]] (not= 0 v)))
       (mapv first)))

(defn- tag-modules
  [treatment-access active-modules-ids]
  (mapv #(if (some #{(:module-id %)} active-modules-ids)
           (assoc % :active true)
           (assoc % :active false))
        (:modules treatment-access)))

(defn- tag-active-treatment-modules [treatment-access]
  (let [active-modules-ids (active-modules treatment-access)
        tagged             (tag-modules treatment-access active-modules-ids)]
    (assoc treatment-access :modules tagged :active-module-ids active-modules-ids)))

(defn- add-worksheets-to-modules
  [modules worksheets]
  (let [worksheets-by-module (group-by :module-id worksheets)]
    (map (fn [module]
           (assoc module :worksheets (map :worksheet-id (get worksheets-by-module (:module-id module)))))
         modules)))

(defn- add-active-worksheets [treatment-access]
  (if (seq (:active-module-ids treatment-access))
    (let [worksheets              (db/get-module-worksheets {:module-ids (:active-module-ids treatment-access)})
          modules-with-worksheets (add-worksheets-to-modules (:modules treatment-access) worksheets)
          worksheets-reduced      (into {} (map #(identity [(:worksheet-id %) (dissoc % :module-id)]) worksheets))]
      (merge treatment-access {:modules modules-with-worksheets :worksheets worksheets-reduced}))
    treatment-access))


(defn user-treatment-info [user-id]
  (let [tx-info (mapv #(-> %
                           add-treatment-modules
                           tag-active-treatment-modules
                           add-active-worksheets) (user-treatments user-id))]
    tx-info))

