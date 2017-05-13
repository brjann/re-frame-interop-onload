(ns bass4.shunting-yard
  (:require [clojure.math.numeric-tower :as math]))

(defn- to-chars
  [x]
  (clojure.string/split (clojure.string/replace x " " "") #""))

(defn- digit?
  [x]
  (and x (re-find #"^\d+$" x)))

(defn- double-op?
  [op]
  (some (partial = op) ["||" "&&" "==" ">=" "<=" "!="]))

;; Adapted from
;; http://eddmann.com/posts/infix-calculator-in-clojure/

(defn tokenize [expr]
  (reverse
    (reduce
      (fn [[t & ts :as tokens] token]
        (cond
          (and (digit? token) (digit? t)) (cons (str t token) ts)
          (double-op? (str t token)) (cons (str t token) ts)
          :else (cons token tokens)))
      '(), (to-chars expr))))


;; 	array('~', '^', '*', '/',  '-', '+', '==', '!=', '>', '<', '>=', '<=', '&&', '||', '(' );

;; http://en.cppreference.com/w/c/language/operator_precedence
(def ops {"||" 1,
          "&&" 2,
          "==" 3, "!=" 3,
          "<" 4, ">" 4, "<=" 4, ">=" 4,
          "+" 5, "-" 5,
          "*" 6, "/" 6,
          "^" 7,
          "~" 10})

(defn- shunting-yard [[rpn stack last-op?] token]
    (let [less-op? #(and (contains? ops %) (<= (ops token) (ops %)))
          not-open-paren? #(not= "(" %)]
      (cond
        (= token "(") [rpn (cons token stack) true]
        (= token ")") [(vec (concat rpn (take-while not-open-paren? stack))) (rest (drop-while not-open-paren? stack)) false]
        (contains? ops token) (if (and (= "-" token) last-op?)
                                (shunting-yard [(conj rpn "0") stack true] "~")
                                [(vec (concat rpn (take-while less-op? stack))) (cons token (drop-while less-op? stack)) true])
        :else [(conj rpn token) stack false])))

(defn parse-tokens [tokens]
  (butlast (flatten
             (reduce
               shunting-yard
               [[] () true]
               tokens))))

(def truthy? (partial not= 0))

(defn bool01
  [x]
  (if x 1 0))

(defn rpn [tokens]
  (let [ops {"||" #(bool01 (or (truthy? %1) (truthy? %2)))
             "&&" #(bool01 (and (truthy? %1) (truthy? %2)))
             "==" #(bool01 (= %1 %2))
             "!=" #(bool01 (not= %1 %2))
             "<"  #(bool01 (< %1 %2))
             ">"  #(bool01 (> %1 %2))
             "<=" #(bool01 (<= %1 %2))
             ">=" #(bool01 (>= %1 %2))
             "+"  +
             "-"  -
             "*"  *
             '"/" /
             "^" math/expt
             "~" -}]
    (first
      (reduce
        (fn [stack token]
          (if (contains? ops token)
            (cons ((ops token) (second stack) (first stack)) (drop 2 stack))
            (cons (read-string token) stack)))
        [] tokens))))

(def log #(do (println %) %))

(def calc (comp rpn parse-tokens tokenize))

(def calc-debug (comp rpn log parse-tokens log tokenize))
;;(calc-debug "3 + 4 * 5 / (3 + 2)")

(defn testx
  [x [y z]]
  (str x y z))