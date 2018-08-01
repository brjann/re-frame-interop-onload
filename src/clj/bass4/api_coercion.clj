(ns bass4.api-coercion
  (:require [clojure.string :as s]))

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

(defn- extract-args
  [args]
  (loop [acc [] args args]
    (if-not (seq args)
      acc
      (if (= :- (second args))
        (recur (conj acc (list (first args) (nth args 2))) (drop 3 args))
        (recur (conj acc (list (first args))) (rest args))))))


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

(defmacro def-api
  [name arg-spec & body]
  (let [arg-list#    (extract-args arg-spec)
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
    `(defn ~name ~(into [] (map first arg-list#))
       ~(if (seq speced-args#)
          `(let [~@let-vector#]
             ~@body)
          `(do ~@body)))))