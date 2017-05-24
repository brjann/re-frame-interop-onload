(ns bass4.infix-parser
  (:require [clojure.math.numeric-tower :as math]
            [clojure.string :as s]))

;; Adapted from
;; http://eddmann.com/posts/infix-calculator-in-clojure/

(defn- to-chars
  [x]
  (clojure.string/split x #""))

;; http://en.cppreference.com/w/c/language/operator_precedence
(def ops {"||" 1,
          "&&" 2,
          "==" 3, "!=" 3,
          "<" 4, ">" 4, "<=" 4, ">=" 4,
          "+" 5, "-" 5,
          "*" 6, "/" 6,
          "^" 7,
          "~" 10})

(def op-chars (concat (distinct (clojure.string/split (clojure.string/join (keys ops)) #"")) ["(" ")" " "]))

(defn- has-op-char?
  [x]
  (some (partial clojure.string/includes? x) op-chars))

(defn- digit?
  [x]
  (and x (re-find #"^\d+$" x)))

(defn- double-op?
  [op]
  (some (partial = op) ["||" "&&" "==" ">=" "<=" "!="]))

(defn tokenize [expr]
  (filter
    #(not= " " %)
    (reverse
            (reduce
              (fn [[t & ts :as tokens] token]
                (cond
                  (not (has-op-char? (str t token))) (cons (str t token) ts)
                  (double-op? (str t token)) (cons (str t token) ts)
                  :else (cons token tokens)))
              '(), (to-chars expr)))))

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

#_(defn token-resolver
  ([] (token-resolver {}))
  ([namespace] (token-resolver namespace nil))
  ([namespace default]
   (fn
     [token]
     (if (nil? (re-find #"^\d*\.?\d*$" token))
       (if-let [res (or (namespace token)
                        (when (= "$" (subs token 0 1))
                          default))]
         res
         (throw (Exception. (str "Var " token " not found"))))
       (read-string token)))))

(defn str->num
  [s]
  (cond
    (number? s) s
    (nil? s) nil
    (re-find #"^\d+\.?\d*$" (s/trim s)) (read-string s)))

(defn token-resolver
  ([namespace] (token-resolver namespace (constantly nil)))
  ([namespace missing-fn]
   (fn
     [token]
     (if-let [res (or
                    (str->num token)
                    (str->num (namespace token))
                    (str->num (missing-fn token)))]
       res
       (throw (Exception. (str "Var " token " not found")))))))

(defn rpn
  ([tokens] (rpn tokens (token-resolver {})))
  ([tokens token-resolver-fn]
   (let [truthy? (partial not= 0)
         bool01 (fn [x] (if x 1 0))
         ops {"||" #(bool01 (or (truthy? %1) (truthy? %2)))
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
             (cons (token-resolver-fn token) stack)))
         [] tokens)))))

(defn calc
  ([expr] (calc expr (token-resolver {})))
  ([expr token-resolver-fn]
   (-> expr
       tokenize
       parse-tokens
       (rpn token-resolver-fn))))

