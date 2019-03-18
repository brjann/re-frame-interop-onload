(ns bass4.services.content-data
  (:require [bass4.db.core :as db]
            [schema.core :as schema]
            [clojure.tools.logging :as log]
            [bass4.layout :as layout]
            [clojure.string :as str]
            [bass4.http-errors :as http-errors]
            [clojure.set :as set])
  (:import (clojure.lang ExceptionInfo)))


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

(defn split-namespace-key-value [[label value]]
  (let [[namespace key] (str/split label #"\$")]
    (when (some empty? [namespace key])
      (http-errors/throw-400! (str "Split pair " label "=" value " failed")))
    [namespace key value]))

(defn remove-identical-data [string-map old-data]
  (filter
    (fn [[namespace value-name value]]
      (let [old-value (get-in old-data [namespace value-name])]
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
  ([data-map treatment-access-id]
   (save-content-data! data-map treatment-access-id {}))
  ([data-map treatment-access-id ns-aliases]
   (when (seq data-map)
     (let [string-map (mapv split-namespace-key-value (into [] data-map))
           string-map (if (seq ns-aliases)
                        (let [ns-aliases-invert (set/map-invert ns-aliases)]
                          (mapv (fn [[namespace key value]]
                                  [(get ns-aliases-invert namespace namespace) key value])
                                string-map))
                        string-map)
           data-names (distinct (map first string-map))
           old-data   (get-content-data treatment-access-id data-names)
           save-data  (add-data-time-and-owner (remove-identical-data string-map old-data) treatment-access-id)]
       (when (< 0 (count save-data))
         (db/save-content-data! {:content-data save-data}))))))