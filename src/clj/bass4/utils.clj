(ns bass4.utils
  (:require [clojure.data.json :as json]
            [bass4.php-clj.safe :refer [php->clj]]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [clojure.math.numeric-tower :as math]
            [clj-time.coerce :as tc]
            [clojure.string :as str]))

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
  ([json] (json-safe json identity))
  ([json key-fn]
   (try (json/read-str json :key-fn key-fn)
        (catch Exception e nil))))

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
    (re-find #"^-?\d+$" (str/trim s)) (read-string s)))

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
  `(let [start#   (. System (nanoTime))
         ret#     ~expr
         elapsed# (/ (double (- (. System (nanoTime)) start#)) 1000000.0)]
     {:val ret# :time elapsed#}))

(defn swap-key!
  [atom key f val-if-empty]
  (swap! atom #(assoc % key (f (or (get % key) val-if-empty)))))

(defn set-key!
  [atom key val]
  (swap! atom #(assoc % key val)))

;; -------------------
;; PHP "define" PARSER
;; -------------------
(def regex-php-constant (let [q "(\"[^\"\\\\]*(\\\\(.|\\n)[^\"\\\\]*)*\"|'[^'\\\\]*(\\\\(.|\\n)[^'\\\\]*)*')"]
                          (re-pattern (str "define\\s*\\(\\s*" q "\\s*,\\s*" q "\\s*\\)\\s*;"))))

(defn- un-escape [str]
  (-> (subs str 1 (dec (count str)))
      (clojure.string/replace #"\\([^\\])" "$1")
      (clojure.string/replace "\\\\" "\\")))

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

;; https://gist.github.com/danielpcox/c70a8aa2c36766200a95
(defn deep-merge [v & vs]
  (letfn [(rec-merge [v1 v2]
            (if (and (map? v1) (map? v2))
              (merge-with deep-merge v1 v2)
              v2))]
    (if (some identity vs)
      (reduce #(rec-merge %1 %2) v vs)
      v)))

(defn kebab-case-keys
  [m]
  (transform-keys ->kebab-case-keyword m))

(defn kebab-case-keyword
  [s]
  (fnil+ ->kebab-case-keyword s))


(defn mean
  ([vs] (mean (reduce + vs) (count vs)))
  ([sm sz] (/ sm sz)))

(defn sd
  ([vs]
   (sd vs (count vs) (mean vs)))
  ([vs sz u]
   (Math/sqrt (/ (reduce + (map #(Math/pow (- % u) 2) vs))
                 sz))))

(defn quantile
  ([p vs]
   (let [svs (sort vs)]
     (quantile p (count vs) svs (first svs) (last svs))))
  ([p c svs mn mx]
   (let [pic (* p (inc c))
         k   (int pic)
         d   (- pic k)
         ndk (if (zero? k) mn (nth svs (dec k)))]
     (cond
       (zero? k) mn
       (= c (dec k)) mx
       (= c k) mx
       :else (+ ndk (* d (- (nth svs k) ndk)))))))

(defn round-to
  [v p]
  (let [x (* (math/expt 10 p) v)
        r (math/round x)]
    (double (/ r (math/expt 10 p)))))

(defn match-regex? [v regex]
  (boolean (re-matches regex v)))

;; ------------------
;;   TIME FUNCTIONS
;; ------------------

;; TODO: Move to b-time when cyclic dependency has been removed
;; Also present in b-time...
(defn to-unix
  [now]
  (-> now
      (tc/to-long)
      (/ 1000)
      (long)))

(defn from-unix
  [timestamp]
  (tc/from-long (* 1000 timestamp)))

(defn ^:dynamic current-time
  []
  (quot (System/currentTimeMillis) 1000))

;; ---------
;;   QUEUE
;; ---------
; https://stackoverflow.com/questions/8938330/clojure-swap-atom-dequeuing
(defn queue-pop!
  [queue]
  (loop []
    (let [q     @queue
          value (peek q)
          nq    (pop q)]
      (if (compare-and-set! queue q nq)
        value
        (recur)))))

(defn queue-add!
  [queue item]
  (swap! queue #(conj % item)))

(defn escape-html
  [s]
  (str/escape s {\< "&lt;"
                 \> "&gt;"
                 \& "&amp;"
                 \" "&quot;"}))

(defn remove-html
  [s]
  (str/replace s #"\<|\>|\&|\"" ""))