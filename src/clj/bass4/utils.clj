(ns bass4.utils
  (:require [clojure.data.json :as json]
            [bass4.php_clj.core :refer [php->clj]]))


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

(defn subs+
  "Returns the substring of s beginning at start inclusive, and ending
  at end (defaults to length of string), exclusive.
  Does not throw exception if bounds are incorrect
  Returns nil if start is out of range or start is larger than end
  If end is out of range, end is set to range"
  ([s start] (subs+ s start (count s)))
  ([s start end]
   (when (and (<= start (count s)) (<= start end))
     (subs s start (min end (count s))))))

(defn json-safe
  [json]
  (try (json/read-str json)
       (catch Exception e nil)))

;; TODO: Should be able to use select-in instead of filter if matching keys are saved.
(defn keep-matching
  [f m]
  (zipmap (keep-indexed #(when (f %2) %1) m) (filter f m)))

(defn key-map-list
  ([s k]
   (key-map-list s k {}))
  ([s k m]
   (if-not (seq s)
     m
     (recur (rest s)
            k
            (assoc m (get (first s) k) (first s))))))

