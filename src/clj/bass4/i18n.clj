(ns bass4.i18n
  (:require [selmer.parser :as parser]
            [taoensso.tempura :as tempura]
            [bass4.utils :refer [map-map map-map-keys filter-map]]
            [bass4.bass-locals :as locals]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as s]
            [mount.core :refer [defstate]]
            [clojure.tools.logging :as log])
  (:import [java.io.File]))

(defn ls [d]
  (remove #(.isDirectory %) (.listFiles d)))

(defn load-langs
  ([] (load-langs (io/file (System/getProperty "user.dir") "i18n")))
  ([dir]
   (->> (ls dir)
        (map #(vector (-> (.getName %)
                          (s/replace  #"[.]edn$" "")
                          (keyword))  %))
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
          (tempura/tr
            {:dict i18n-map
             :missing-resource-fn
                   (fn
                     [{:keys [opts locales resource-ids resource-args]}]
                     (str "Missing translation keys: " resource-ids))}
            [(locals/language)]
            resource-ids
            resource-args)))



