(ns bass4.utils
  (:require [clojure.data.json :as json]
            [bass4.php_clj.core :refer [php->clj]]
            [clojure.string :as s]))


(defn unserialize-key
  ([m k] (unserialize-key m k identity))
  ([m k f]
   (->> (get m k)
        (php->clj)
        (#(if (= (class %) flatland.ordered.map.OrderedMap) (into {} %) %))
        f
        (assoc {} k)
        (merge m))))

(defn arity [f]
  (let [m (first (.getDeclaredMethods (class f)))
        p (.getParameterTypes m)]
    (alength p)))

(defn map-map [f m]
  (let [ks (keys m)
        vs (vals m)]
    (zipmap ks (mapv f vs))))

(defn filter-map [f m]
  (->> (filter #(f (val %)) m)
       (into {})))

(defn map-map-keys [f m]
  (let [ks (keys m)
        vs (vals m)]
    (zipmap ks (mapv f vs ks))))

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

;; https://stackoverflow.com/questions/8641305/find-index-of-an-element-matching-a-predicate-in-clojure
(defn indices [pred coll]
  (keep-indexed #(when (pred %2) %1) coll))

(defn fnil+ [f x]
  "Returns nil if x is nil, else (f x)"
  (when-not (nil? x)
    (f x)))

(defn str->int
  [s]
  (cond
    (integer? s) s
    (nil? s) nil
    (re-find #"^\d+$" (s/trim s)) (read-string s)))

;; https://github.com/clojure/clojure/blob/clojure-1.9.0-alpha14/src/clj/clojure/core.clj#L519
(defn boolean?
  "Return true if x is a Boolean"
  {:added "1.9"}
  [x] (instance? Boolean x))

(defn val-to-bool
  [x]
  (cond
    (boolean? x) x
    (nil? x) false
    :else (not (zero? (str->int x)))))

(defn diff
  [s1 s2]
  (filter #(not (some (partial = %) s2)) s1))

;; https://stackoverflow.com/questions/3249334/test-whether-a-list-contains-a-specific-value-in-clojure
(defn in?
  "true if coll contains m"
  [coll m]
  (some #(= m %) coll))

;; http://blog.jayfields.com/2011/01/clojure-select-keys-select-values-and.html
(defn select-values [map ks]
  (reduce #(conj %1 (map %2)) [] ks))

(defmacro time+
  "Evaluates expr and returns a map with a :val and :time keys"
  [expr]
  `(let [start# (. System (nanoTime))
         ret# ~expr
         elapsed# (/ (double (- (. System (nanoTime)) start#)) 1000000.0)]
     {:val ret# :time elapsed#}))

(defn swap-key!
  [atom key f val-if-empty]
  (swap! atom #(assoc % key (f (or (get % key) val-if-empty)))))

(defn set-key!
  [atom key val]
  (swap! atom #(assoc % key val)))

;; -----------------
;; PHP define PARSER
;; -----------------
(def regex-php-constant (let [q "(\"[^\"\\\\]*(\\\\(.|\\n)[^\"\\\\]*)*\"|'[^'\\\\]*(\\\\(.|\\n)[^'\\\\]*)*')"]
                          (re-pattern (str "define\\s*\\(\\s*" q "\\s*,\\s*" q "\\s*\\)\\s*;"))))

(defn- un-escape [str]
  (-> (subs str 1 (dec (count str)))
      (clojure.string/replace  #"\\([^\\])" "$1")
      (clojure.string/replace  "\\\\" "\\")))

(defn- parse-constants-rec [matcher]
  (let [match (re-find matcher)]
    (when match
      (assoc (parse-constants-rec matcher) (keyword (un-escape (nth match 1))) (un-escape (nth match 6))))))

(defn parse-php-constants
  [text]
  (let [matcher (re-matcher regex-php-constant text)]
    (parse-constants-rec matcher)))

(defn nil-zero?
  [x]
  (or (nil? x) (zero? x)))