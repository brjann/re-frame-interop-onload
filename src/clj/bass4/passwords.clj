(ns bass4.passwords
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.math.numeric-tower :as math]
            [bass4.utils :refer [map-map map-map-keys filter-map]]
            [bass4.clients.core :as clients]))

(def password-chars [2 3 4 6 7 8 9 "a" "b" "d" "e" "g" "h" "p" "r" "A" "B" "C" "D" "E" "F" "G" "H" "J" "K" "L" "M" "N" "P" "Q" "R" "T" "W" "X" "Y" "Z"])

(defn ^:dynamic letters-digits
  ([length] (letters-digits length password-chars))
  ([length chars]
   (clojure.string/join
     ""
     (map
       #(get chars %1)
       (repeatedly length #(rand-int (- (count chars) 1)))))))

(defn words
  [lang]
  (let [file (or (io/resource (str "docs/words-" lang ".txt"))
                 (io/resource "docs/words-en.txt"))]
    (->
      file
      (slurp)
      (string/split-lines)
      (into []))))

(defn random-number
  [digits]
  (let [max (inc (+ (math/expt 10 digits)))
        add (math/expt 10 (dec digits))]
    (+ (rand-int (- max add)) add)))

(defn random-word
  [words]
  (get words (rand-int (count words))))

(defn ^:dynamic password
  ([] (password :word 3 :word))
  ([& config]
   (let [words (words (clients/client-setting [:language]))]
     (apply str (mapv (fn [x]
                        (if (integer? x)
                          (random-number x)
                          (random-word words)))
                      config)))))