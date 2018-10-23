(ns bass4.i18n
  (:require [taoensso.tempura :as tempura]
            [bass4.utils :refer [map-map map-map-keys filter-map deep-merge in?]]
            [bass4.db-config :as db-config]
            [bass4.php_clj.reader :as reader]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as s]
            [flatland.ordered.map :refer [ordered-map]]
            [mount.core :refer [defstate]]
            [clojure.tools.logging :as log])
  (:import (java.io BufferedInputStream File)
           (mount.core DerefableState)))

(defn ls [^File d]
  (remove #(.isDirectory %) (.listFiles d)))

(defn load-langs
  ([] (load-langs (io/file (System/getProperty "user.dir") "i18n")))
  ([dir]
   (->> (ls dir)
        (map #(vector (-> (.getName %)
                          (s/replace #"[.]edn$" "")
                          (keyword)) %))
        (into {})
        (map-map slurp)
        (map-map-keys #(try (edn/read-string %1)
                            (catch Exception e
                              (log/error (str "Could not read language file " %2)))))
        (filter-map identity))))

(defstate i18n-map
  :start (load-langs))

(defn tr
  ([resource-ids] (tr resource-ids []))
  ([resource-ids resource-args]
   (when (= DerefableState (class i18n-map))
     (throw (Exception. "i18n accessed before started by mount.")))
   (tempura/tr
     {:dict i18n-map
      :missing-resource-fn
            (fn
              [{:keys [resource-ids]}]
              (str "Missing translation keys: " resource-ids))}
     [(or (db-config/language) "en")]
     resource-ids
     resource-args)))

;; --------------------
;;   LANG FILE MERGER
;; --------------------

(defn- parse-string
  [s]
  (loop [acc [] s s]
    (if (zero? (.available s))
      (s/join acc)
      (let [c (reader/read-char s)]
        (cond
          (= \" c)
          (s/join acc)

          (= \\ c)
          (recur (conj acc (reader/read-char s)) s)

          :else
          (recur (conj acc c) s))))))

(defn- parse-keyword
  [s]
  (loop [acc [] s s]
    (if (zero? (.available s))
      [(keyword (s/join acc))]
      (do (.mark s 1)
          (let [c (reader/read-char s)]
            (if (or (in? [\( \) \{ \} \, \"] c)
                    (s/blank? (str c)))
              (do (.reset s)
                  (keyword (s/join acc)))
              (recur (conj acc c) s)))))))

(defn- i18n-map-to-list*
  [^BufferedInputStream s]
  (loop [acc [] s s]
    (if (zero? (.available s))
      acc
      (let [c (reader/read-char s)]
        (if (or (= \) c) (= \} c))
          acc
          (let [form (cond
                       (= \: c)
                       (parse-keyword s)

                       (= \" c)
                       (parse-string s)

                       (or (= \( c) (= \{ c))
                       (i18n-map-to-list* s))]
            (recur (if form (conj acc form) acc) s)))))))

(defn i18n-map-to-list
  [s]
  (first (i18n-map-to-list* (reader/buffered-input-stream s))))

(defn- i18n-list-to-str
  ([i18n-list new-map] (i18n-list-to-str i18n-list new-map []))
  ([i18n-list new-map keys]
   (loop [acc [] i18n-list i18n-list]
     (if (nil? (seq i18n-list))
       (str "{" (s/join \newline acc) "}")
       (let [[key value] (take 2 i18n-list)]
         (if (vector? value)
           (let [sub-map     (i18n-list-to-str value new-map (conj keys key))
                 key-sub-map (str key " " sub-map)]
             (recur (conj acc key-sub-map) (nthrest i18n-list 2)))
           (let [value     (get-in new-map (conj keys key))
                 key-value (str key " \"" (s/escape value {\" "\\\"" \newline "\\n"}) "\"")]
             (recur (conj acc key-value) (nthrest i18n-list 2)))))))))

(defn merge-i18n
  [lang]
  (let [new-map   (deep-merge
                    (:en i18n-map)
                    (get i18n-map (keyword lang)))
        i18n-list (-> (System/getProperty "user.dir")
                      (io/file "i18n/en.edn")
                      (slurp)
                      (i18n-map-to-list))]
    (i18n-list-to-str i18n-list new-map)))
