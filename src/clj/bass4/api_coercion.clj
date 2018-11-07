(ns bass4.api-coercion
  (:require [clojure.string :as s]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [struct.core :as st]
            [bass4.utils :as utils :refer [map-map]])
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

(defn url?
  [s]
  (let [test-url (try (URL. s)
                      (catch Exception _
                        (try
                          (URL. try-url s)
                          (catch Exception _))))]
    (try
      (when-not (empty? (.getHost test-url))
        s)
      (catch Exception _))))

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
            nil
            #_(throw (Exception.)))]
    x))

(defn bool!
  [x]
  (if (boolean? x)
    x
    (let [x (int! x)]
      (not (zero? x)))))



(defn JSON-map!
  [s]
  (let [m (json/read-str s)]
    (when-not (map? m)
      (throw (Exception.)))
    m))

(defn json!
  [s]
  (try (json/read-str s)
       (catch Exception _)))

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

(defn str?
  [s min max]
  (when (string? s)
    (let [l (count s)]
      (and (<= min l) (>= max l)))))

(defn ?map?
  [m]
  (or (nil? m) (map? m)))

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
                                        (str ". Message: " msg)))
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


(defn api-exception
  [message spec arg-name v]
  (let [v (if (and (string? v) (< 20 (count v)))
            (str (subs v 0 20) "... " (- (count v) 20) " more chars")
            v)]
    (throw (ex-info
             (str message "spec: " spec ", parameter: " arg-name ", value: " (subs (str (class v)) 6) "(" v ")")
             {:type  ::api-exception
              :spec  spec
              :param arg-name
              :value v}))))

(defmacro defapi
  [api-name & more]
  (when-not (instance? Symbol api-name)
    (throw (IllegalArgumentException. "First argument to def-api must be a symbol")))
  (let [[doc-string
         arg-spec
         body] (destruct-api more)
        arg-list    (extract-args arg-spec)
        speced-args (filter #(= 2 (count %)) arg-list)
        spec-fn     (fn [arg spec-type spec]
                      (let [s   (if (vector? spec) spec [spec])
                            v   (gensym)
                            res (gensym)]
                        `(fn [~v]
                           (let [~res (~(first s) ~v ~@(rest s))]
                             ~(case spec-type
                                :validate
                                `(if ~res
                                   ~v
                                   (throw (api-exception "API validation failed. " ~(str s) ~(str arg) ~v))
                                   #_(throw (Exception. (str "Validation " ~(str s) " of parameter " ~(str arg) " with value \"" ~v "\" failed"))))

                                :coerce
                                `(if (nil? ~res)
                                   (throw (api-exception "API coercion failed. " ~(str s) ~(str arg) ~v))
                                   ~res))))))
        parse-spec  (fn [arg spec]
                      ;; This THROWS if spec is not symbol
                      (let [spec-name (name (if (vector? spec)
                                              (first spec)
                                              spec))]
                        (cond
                          (= \? (last spec-name))
                          (list (spec-fn arg :validate spec))

                          (= \! (last spec-name))
                          (list (spec-fn arg :coerce spec))

                          :else
                          (throw (Exception. (str "Spec functions must end with ! (coercion) or ? (validation). \""
                                                  spec-name "\" did not."))))))
        let-vec     (mapv (fn [[arg specs]]
                            (let [specs       (if (vector? specs) specs [specs])
                                  [nil-ok? specs] (if (utils/in? specs :?)
                                                    [true (filterv #(not= :? %) specs)]
                                                    [false specs])
                                  spec-fns    (mapv #(parse-spec arg %) specs)
                                  spec-thread `(-> ~arg
                                                   ~@spec-fns)]
                              [arg (if nil-ok?
                                     `(when-not (nil? ~arg)
                                        ~spec-thread)
                                     `(if (nil? ~arg)
                                        (throw (ex-info
                                                 (str "API validation failed. nil error. param: " ~(str arg))
                                                 {:type ::api-exception}))
                                        ~spec-thread))]))
                          speced-args)]
    (concat
      `(defn ~api-name)
      (when doc-string
        (list doc-string))
      (list (into [] (map first arg-list)))
      (list (if (seq let-vec)
              `(let [~@(reduce concat let-vec)]
                 ~@body)
              `(do ~@body))))))
