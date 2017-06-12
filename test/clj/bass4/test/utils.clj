(ns bass4.test.utils
  (:require [clojure.test :refer :all]
            [bass4.utils :refer :all]))



;(defn unserialize-key
;  ([m k] (unserialize-key m k identity))
;  ([m k f]
;   (->> (get m k)
;        (php->clj)
;        (#(if (= (class %) flatland.ordered.map.OrderedMap) (into {} %) %))
;        f
;        (assoc {} k)
;        (merge m))))

(deftest t-unserialize-key
  (is (= {:hej {"hej" "då"}} (unserialize-key {:hej "a:1:{s:3:\"hej\";s:3:\"då\";}"} :hej)))
  (is (= {:hej [2 3 4]} (unserialize-key {:hej "a:3:{i:0;i:1;i:1;i:2;i:2;i:3;}"} :hej #(mapv inc %)))))

;
;(defn arity [f]
;  (let [m (first (.getDeclaredMethods (class f)))
;        p (.getParameterTypes m)]
;    (alength p)))

(deftest t-arity
  (is (= 2 (arity (fn [x y]))))
  (is (= 0 (arity (fn [])))))
;
;(defn map-map [f m]
;  (let [ks (keys m)
;        vs (vals m)]
;    (zipmap ks (mapv f vs))))

(deftest t-map-map
  (is (= {:hej 2 :då 9} (map-map inc {:hej 1 :då 8}))))
;
;(defn filter-map [f m]
;  (->> (filter #(f (val %)) m)
;       (into {})))
;
;(defn map-map-keys [f m]
;  (let [ks (keys m)
;        vs (vals m)]
;    (zipmap ks (mapv f vs ks))))
;
;(defn subs+
;  "Returns the substring of s beginning at start inclusive, and ending
;  at end (defaults to length of string), exclusive.
;  Does not throw exception if bounds are incorrect
;  Returns nil if start is out of range or start is larger than end
;  If end is out of range, end is set to range"
;  ([s start] (subs+ s start (count s)))
;  ([s start end]
;   (when (and (<= start (count s)) (<= start end))
;     (subs s start (min end (count s))))))
;
;(defn json-safe
;  [json]
;  (try (json/read-str json)
;       (catch Exception e nil)))
;
;(defn keep-matching
;  [f m]
;  (zipmap (keep-indexed #(when (f %2) %1) m) (filter f m)))
;
;(defn key-map-list
;  ([s k]
;   (key-map-list s k {}))
;  ([s k m]
;   (if-not (seq s)
;     m
;     (recur (rest s)
;            k
;            (assoc m (get (first s) k) (first s))))))
;
;;; https://stackoverflow.com/questions/8641305/find-index-of-an-element-matching-a-predicate-in-clojure
;(defn indices [pred coll]
;  (keep-indexed #(when (pred %2) %1) coll))
;
;(defn fnil+ [f x]
;  "Returns nil if x is nil, else (f x)"
;  (when-not (nil? x)
;    (f x)))
;
;(defn str->int
;  [s]
;  (cond
;    (integer? s) s
;    (nil? s) nil
;    (re-find #"^\d+$" (s/trim s)) (read-string s)))

(deftest t-str->int
  (is (= 3 (str->int "3")))
  (is (= 3 (str->int 3)))
  (is (= nil (str->int nil))))
;
;(defn diff
;  [s1 s2]
;  (filter #(not (some (partial = %) s2)) s1))

(deftest t-diff
  (is (= [1 2] (diff [1 2 3] [3 4 5])))
  (is (= [0 1 2] (diff [0 1 2 3] [3 4 5]))))
;
;;; https://stackoverflow.com/questions/3249334/test-whether-a-list-contains-a-specific-value-in-clojure
;(defn in?
;  "true if coll contains m"
;  [coll m]
;  (some #(= m %) coll))
;
;;; http://blog.jayfields.com/2011/01/clojure-select-keys-select-values-and.html
;(defn select-values [map ks]
;  (reduce #(conj %1 (map %2)) [] ks))
;
;(defmacro time+
;  "Evaluates expr and returns a map with a :val and :time keys"
;  [expr]
;  `(let [start# (. System (nanoTime))
;         ret# ~expr
;         elapsed# (/ (double (- (. System (nanoTime)) start#)) 1000000.0)]
;     {:val ret# :time elapsed#}))
;
;(defn swap-key!
;  [atom key f val-if-empty]
;  (swap! atom #(assoc % key (f (or (get % key) val-if-empty)))))
