(ns bass4.services.content-data
  (:require [bass4.db.core :as db]
            [schema.core :as schema]
            [clojure.tools.logging :as log]
            [bass4.layout :as layout])
  (:import (clojure.lang ExceptionInfo)))


;; ****************************
;;   METHODS FOR LOADING DATA
;; ****************************

(defn reduce-content-map [m [x & xs :as s]]
  (if (empty? s)
    m
    (recur (merge m (assoc m (keyword (:valuename x)) (:value x))) xs)))

(defn unflatten-content-data
  [s]
  (reduce (fn [m x] (update-in m [(keyword (:dataname x))] #(conj % x))) {} s))

(defn content-data-transform [content-data]
  {(key content-data) (->> (val content-data)
                           (map #(select-keys % [:valuename :value]))
                           (reduce-content-map {}))})


#_(defn get-content-data [data-owner-id data-names]
    (reduce merge
            (map content-data-transform
                 (unflatten-content-data
                   (db/get-content-data {:data-owner-id data-owner-id :data-names data-names})))))

(defn get-content-data [data-owner-id data-names]
  (->>
    (db/get-content-data {:data-owner-id data-owner-id :data-names data-names})
    (unflatten-content-data)
    (map content-data-transform)
    (reduce merge)))

(defn split-first [pair]
  (let [splitted-list (clojure.string/split (first pair) #"\.")
        data-name     (first splitted-list)
        key           (clojure.string/join "." (rest splitted-list))
        value         (second pair)]
    (when (or
            (some #(or (nil? %) (= "" %)) [data-name key])
            (not (string? value)))
      (layout/throw-400! (str "Split pair " pair " failed")))
    [data-name key value]))

(defn remove-identical-data [string-map old-data]
  (filter
    (fn [[data-name value-name value]]
      (let [old-value (get-in old-data [(keyword data-name) (keyword value-name)])]
        (if (= old-value nil)
          (not= value "")
          (not= old-value value))
        ))
    string-map))

(defn add-data-time-and-owner [string-map treatment-access-id]
  (map #(concat
          [treatment-access-id
           ;; TODO: Should this timestamp be used?
           (quot (System/currentTimeMillis) 1000)]
          %) string-map))

(defn save-content-data!
  [data-map treatment-access-id]
  (when (seq data-map)
    (let [string-map (mapv split-first (into [] data-map))
          data-names (distinct (map first string-map))
          old-data   (get-content-data treatment-access-id data-names)
          save-data  (add-data-time-and-owner (remove-identical-data string-map old-data) treatment-access-id)]
      (when (< 0 (count save-data))
        (db/save-content-data! {:content-data save-data})))))