(ns bass4.instrument.flagger
  (:require [bass4.db.core :as db]
            [clj-time.core :as t]
            [bass4.php-clj.safe :refer [php->clj]]
            [bass4.utils :as utils]
            [clojure.string :as str]
            [bass4.infix-parser :as infix]
            [bass4.instrument.preview :as instruments]))

(defn db-flagging-specs
  [db]
  (let [res (db/answers-flagging-specs db {})]
    (->> (merge
           {:test (:test res)}
           (php->clj (:projects res)))
         (utils/filter-map not-empty)
         (utils/map-map str/split-lines))))

(defn- parse-spec
  [spec]
  (let [[instrument condition & msgv] (str/split spec #":")
        msg (when msgv
              (str/trim (str/join ":" msgv)))]
    (when (and (not-empty instrument) condition)
      {:instrument (str/trim instrument)
       :condition  (str/trim condition)
       :message    msg})))

(defn flagging-specs
  [db]
  (let [specs-per-project (db-flagging-specs db)]
    (utils/map-map #(map parse-spec %) specs-per-project)))

(defn- namespace-map
  [instrument answers]
  (let [merged (instruments/merge-items-answers instrument answers)]
    (merge (:sums merged)
           (->> (concat (map (juxt #(str "@" (:name %)) :value) (:items merged))
                        (map (juxt #(str (first %) "_spec") second) (:specifications merged)))
                (into {})))))

(defn eval-condition
  [condition namespace]
  (let [resolver (infix/token-resolver namespace)]
    (infix/calc condition resolver)))




