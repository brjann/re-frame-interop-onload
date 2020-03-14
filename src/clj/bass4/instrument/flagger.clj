(ns bass4.instrument.flagger
  (:require [bass4.db.core :as db]
            [clj-time.core :as t]
            [bass4.php-clj.safe :refer [php->clj]]
            [bass4.utils :as utils]
            [clojure.string :as str]
            [bass4.infix-parser :as infix]
            [bass4.api-coercion :as api]
            [bass4.instrument.answers-services :as instrument-answers]))

(defn db-flagging-specs
  [db]
  (let [res      (db/answers-flagging-specs db {})
        projects (let [x (php->clj (:projects res))]
                   (if-not (map? x)
                     {}
                     x))]
    (->> (merge
           {:test   (:test res)
            :global (get projects 100)}
           (dissoc projects 100))
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

(defn ^:dynamic flagging-specs
  [db]
  (let [specs-per-project (db-flagging-specs db)]
    (utils/map-map #(map parse-spec %) specs-per-project)))

(defn instrument-specs
  [instrument]
  (fn [specs]
    (filter #(or (and (:instrument-id %)
                      (= (:instrument-id %) (:instrument-id instrument)))
                 (and (:abbreviation % %)
                      (= (:abbreviation % %) (:abbreviation instrument))))
            specs)))

(defn filter-specs
  [instrument project-specs]
  (->> project-specs
       (utils/map-map (instrument-specs instrument))
       (utils/filter-map seq)))

(defn namespace-map
  [item-answers]
  (merge (->> (concat (map (juxt #(str "@" (:name %)) :value) (:items item-answers))
                      (->> (:items item-answers)
                           (filter :specification)
                           (map (juxt #(str "@" (:name %) "_spec") :specification))))
              (into {}))
         (:sums item-answers)))

(defn eval-spec
  [spec namespace]
  (let [condition (:condition spec)]
    (try
      (let [resolver (infix/token-resolver namespace)
            parsed   (-> condition
                         (infix/tokenize)
                         (infix/parse-tokens))
            res      (infix/rpn parsed resolver)
            message  (or (:message spec)
                         (-> (apply str "Answers were " (->> parsed
                                                             (filter #(contains? namespace %))
                                                             (map #(str % "=" (get namespace %)))
                                                             (interpose ", ")))
                             (str/replace #"@" "item ")))]
        (assoc spec :match? (not (zero? res))
                    :message message))
      (catch Exception e
        (assoc spec :error (.getMessage e))))))

(defn flag-answer!
  [db project-id instrument answers-map]
  (let [item-answers   (instrument-answers/merge-items-answers
                         instrument
                         answers-map)
        projects-specs (filter-specs
                         instrument
                         (get (flagging-specs db) project-id))
        namespace      (namespace-map item-answers)]
    (utils/map-map
      (fn [specs]
        (map (fn [spec]
               (eval-spec spec namespace))
             specs))
      projects-specs)))
