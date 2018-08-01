(ns bass4.api-coercion
  (:require [clojure.string :as s]
            [clojure.tools.logging :as log])
  (:import (clojure.lang Symbol)
           (java.net URI)))

(defn URL
  "A very basic URL validator which does very limited validation
  of relative URLs"
  [s]
  ;; This will throw exception if URI is not valid
  (URI. s)
  s)

(defn URL?
  [s]
  (when-not (nil? s) (URL s)))

(defn Int
  [s]
  (let [x (cond
            (integer? s) s
            (re-find #"^\d+$" (s/trim s)) (read-string s))]
    (if (nil? x)
      (throw (Exception.))
      x)))


(def Str
  str)

(defn Str?
  [s]
  (if (nil? s)
    nil
    (str s)))

(defn Str+
  [s]
  (let [s (str s)]
    (if (zero? (count s))
      (throw (Exception.))
      s)))

(defn Map
  [v]
  (if (map? v)
    v
    (throw (Exception.))))




(defn eval-spec
  [api-name spec v spec-name v-name]
  (try (spec v)
       (catch Exception e
         (let [msg (.getMessage e)]
           (throw (ex-info
                    (str "API validation failed. API: "
                         api-name
                         ". Spec-name: "
                         spec-name
                         ". Arg name: " v-name
                         ". Arg value: " v
                         (when-not (zero? (count msg))
                           ". Message: ") (.getMessage e))
                    {:type ::api-exception}))))))

(defn- extract-args
  [args]
  (loop [acc [] args args]
    (if-not (seq args)
      acc
      (if (= :- (second args))
        (recur (conj acc (list (first args) (nth args 2))) (drop 3 args))
        (recur (conj acc (list (first args))) (rest args))))))

(defn- destruct-api
  [more]
  (let [[doc-string more] (if (string? (first more))
                            [(first more) (rest more)]
                            [nil more])
        [arg-spec body] [(first more) (rest more)]]
    [doc-string arg-spec body]))

(defmacro def-api
  [name & more]
  (when-not (instance? Symbol name)
    (throw (IllegalArgumentException. "First argument to def-api must be a symbol")))
  (let [[doc-string# arg-spec# body#] (destruct-api more)
        arg-list#    (extract-args arg-spec#)
        ns#          (ns-name *ns*)
        speced-args# (filter #(= 2 (count %)) arg-list#)
        let-vector#  (reduce #(concat %1 (list (first %2) `(eval-spec
                                                             ~(str ns# "/" name)
                                                             ~(second %2)
                                                             ~(first %2)
                                                             ~(str (second %2))
                                                             ~(str (first %2)))))
                             nil
                             speced-args#)]
    (concat
      `(defn ~name)
      (when doc-string#
        (list doc-string#))
      (list (into [] (map first arg-list#)))
      (list (if (seq speced-args#)
              `(let [~@let-vector#]
                 ~@body#)
              `(do ~@body#))))))