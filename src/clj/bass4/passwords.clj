(ns bass4.passwords
  (:require [bass4.utils :refer [map-map map-map-keys filter-map]]
            [bass4.db-config :as db-config]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as s]
            [clojure.math.numeric-tower :as math]
            [mount.core :refer [defstate]]
            [clojure.tools.logging :as log]
            [bass4.services.bass :as bass]
            [clojure.string :as string])
  (:import [java.io.File]))

(def password-chars [2 3 4 6 7 8 9 "a" "b" "d" "e" "g" "h" "p" "r" "A" "B" "C" "D" "E" "F" "G" "H" "J" "K" "L" "M" "N" "P" "Q" "R" "T" "W" "X" "Y" "Z"])

(defn letters-digits
  [length]
  (clojure.string/join
    ""
    (map
      #(get password-chars %1)
      (repeatedly length #(rand-int (- (count password-chars) 1))))))

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

(defn password
  ([] (password :word 3 :word))
  ([& config]
   (let [words (words (db-config/language))]
     (apply str (mapv (fn [x]
                        (if (integer? x)
                          (random-number x)
                          (random-word words)))
                      config)))))