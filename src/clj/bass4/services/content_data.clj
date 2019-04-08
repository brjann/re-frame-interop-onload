(ns bass4.services.content-data
  (:require [clojure.set :as set]
            [bass4.db.core :as db]
            [bass4.utils :as utils]))


;; ****************************
;;   METHODS FOR LOADING DATA
;; ****************************

(defn reduce-content-map [m [x & xs :as s]]
  (if (empty? s)
    m
    (recur (merge m (assoc m (:valuename x) (:value x))) xs)))

(defn unflatten-content-data
  [s]
  (reduce (fn [m x] (update-in m [(:dataname x)] #(conj % x))) {} s))

(defn content-data-transform [content-data]
  {(key content-data) (->> (val content-data)
                           (map #(select-keys % [:valuename :value]))
                           (reduce-content-map {}))})

(defn get-content-data
  [data-owner-id data-names]
  (->>
    (db/get-content-data {:data-owner-id data-owner-id :data-names data-names})
    (unflatten-content-data)
    (map content-data-transform)
    (reduce merge)))

(defn get-content-data-namespaces
  [data-owner-id]
  (->>
    (db/get-content-data-namespaces {:data-owner-id data-owner-id})
    (map :dataname)))

(defn namespaces-last-updates
  [treatment-access-id]
  (->> (db/get-content-data-last-save {:data-owner-id treatment-access-id})
       (group-by :namespace)
       (utils/map-map first)))

(defn remove-identical-data [string-map old-data]
  (filter
    (fn [[namespace value-name value]]
      (let [old-value (get-in old-data [namespace value-name])]
        (if (= old-value nil)
          (not= value "")
          (not= old-value value))))
    string-map))

(defn add-data-time-and-owner [string-map treatment-access-id]
  (map #(concat
          [treatment-access-id
           ;; TODO: Should this timestamp be used?
           (quot (System/currentTimeMillis) 1000)]
          %) string-map))

(defn save-api-content-data!
  ([data-vec treatment-access-id]
   (save-api-content-data! data-vec treatment-access-id {}))
  ([data-vec treatment-access-id ns-aliases]
   (when (seq data-vec)
     (let [data-vec   (if (seq ns-aliases)
                        (let [ns-aliases-invert (set/map-invert ns-aliases)]
                          (mapv (fn [[namespace key value]]
                                  [(get ns-aliases-invert namespace namespace) key value])
                                data-vec))
                        data-vec)
           data-names (distinct (map first data-vec))
           old-data   (get-content-data treatment-access-id data-names)
           save-data  (add-data-time-and-owner (remove-identical-data data-vec old-data) treatment-access-id)]
       (when (< 0 (count save-data))
         (db/save-content-data! {:content-data save-data}))))))