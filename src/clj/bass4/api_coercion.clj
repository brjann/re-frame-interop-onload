(ns bass4.api-coercion
  (:require [clojure.string :as s]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json])
  (:import (clojure.lang Symbol)
           (java.net URL)))

(def try-url (URL. "http://example.com"))

(defn URL?
  [s]
  (let [test-url (try (URL. s)
                      (catch Exception _
                        (URL. try-url s)))]
    (when (empty? (.getHost test-url))
      (throw (Exception.))))
  s)

(defn ?URL?
  [s]
  (when-not (nil? s) (URL? s)))

(defn int!
  [i]
  (let [x (cond
            (integer? i)
            i

            (re-find #"^\d+$" (s/trim i))
            (read-string i)

            :else
            (throw (Exception.)))]
    x))

(defn JSON-map!
  [s]
  (let [m (json/read-str s)]
    (when-not (map? m)
      (throw (Exception.)))
    m))

(defn ?str!
  [s]
  (if (nil? s)
    nil
    (str s)))

(defn str+!
  [s]
  (let [s (str s)]
    (if (zero? (count s))
      (throw (Exception.))
      s)))

(defn eval-spec
  [api-name spec v spec-name v-name]
  (let [api-exception (fn [msg]
                        (throw (ex-info
                                 (str "API validation failed. API: "
                                      api-name
                                      ". Spec-name: "
                                      spec-name
                                      ". Arg name: " v-name
                                      ". Arg value: " v
                                      (when-not (empty? msg)
                                        ". Message: " msg))
                                 {:type ::api-exception})))
        validation?   (= \? (last spec-name))
        nil-ok?       (= \? (first spec-name))]
    (try (let [ret (spec v)]
           (if validation?
             (if (or ret (and (nil? v) nil-ok?))
               v
               (api-exception "Validation failed"))
             ret))
         (catch Throwable e
           (let [msg (.getMessage e)]
             (api-exception msg))))))

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
  [api-name & more]
  (when-not (instance? Symbol api-name)
    (throw (IllegalArgumentException. "First argument to def-api must be a symbol")))
  (let [[doc-string# arg-spec# body#] (destruct-api more)
        arg-list#    (extract-args arg-spec#)
        ns#          (ns-name *ns*)
        speced-args# (filter #(= 2 (count %)) arg-list#)
        let-vector#  (reduce #(concat %1 (list (first %2) `(eval-spec
                                                             ~(str ns# "/" api-name)
                                                             ~(second %2)
                                                             ~(first %2)
                                                             ~(name (second %2))
                                                             ~(name (first %2)))))
                             nil
                             speced-args#)]
    (concat
      `(defn ~api-name)
      (when doc-string#
        (list doc-string#))
      (list (into [] (map first arg-list#)))
      (list (if (seq speced-args#)
              `(let [~@let-vector#]
                 ~@body#)
              `(do ~@body#))))))