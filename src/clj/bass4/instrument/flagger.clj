(ns bass4.instrument.flagger
  (:require [bass4.db.core :as db]
            [clj-time.core :as t]
            [bass4.php-clj.safe :refer [php->clj]]
            [bass4.utils :as utils]
            [clojure.string :as str]
            [bass4.infix-parser :as infix]
            [bass4.api-coercion :as api]))

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
        msg                   (when msgv
                                (str/trim (str/join ":" msgv)))
        instrument-identifier (str/trim instrument)
        instrument-id         (api/->int instrument-identifier)]
    (when (and (not-empty instrument) condition)
      {:instrument-id instrument-id
       :abbreviation  (when-not instrument-id
                        instrument-identifier)
       :condition     (str/trim condition)
       :message       msg})))

(defn flagging-specs
  [db]
  (let [specs-per-project (db-flagging-specs db)]
    (utils/map-map #(map parse-spec %) specs-per-project)))

(defn filter-specs
  [instrument project-specs]
  (->> project-specs
       (utils/map-map (fn [p]
                        (filter #(or (and (:instrument-id %)
                                          (= (:instrument-id %) (:instrument-id instrument)))
                                     (and (:abbreviation % %)
                                          (= (:abbreviation % %) (:abbreviation instrument))))
                                p)))
       (utils/filter-map seq)))

(defn namespace-map
  [item-answers]
  (merge (->> (concat (map (juxt #(str "@" (:name %)) :value) (:items item-answers))
                      (->> (:items item-answers)
                           (filter :specification)
                           (map (juxt #(str "@" (:name %) "_spec") :specification))))
              (into {}))
         (:sums item-answers)))

(defn eval-condition
  [condition namespace]
  (let [resolver (infix/token-resolver namespace)]
    (infix/calc condition resolver)))

(defn eval-spec
  [spec namespace]
  (let [condition (:condition spec)]
    (try
      (let [res (eval-condition condition namespace)]
        (assoc spec :match? (not (zero? res))))
      (catch Exception e
        (assoc spec :error (.getMessage e))))))

