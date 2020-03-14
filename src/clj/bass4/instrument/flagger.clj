(ns bass4.instrument.flagger
  (:require [bass4.db.core :as db]
            [clj-time.core :as t]
            [bass4.php-clj.safe :refer [php->clj]]
            [bass4.utils :as utils]
            [clojure.string :as str]
            [bass4.infix-parser :as infix]
            [bass4.api-coercion :as api]
            [bass4.instrument.answers-services :as instrument-answers]
            [bass4.db.orm-classes :as orm]))

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

(defn- instrument-specs
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

(defn- project-instrument-specs
  [flagging-specs project-id instrument]
  (apply concat (vals (filter-specs
                        instrument
                        (select-keys flagging-specs [project-id :global])))))

(defn- apply-instrument-specs
  [instrument-specs namespace]
  (->> instrument-specs
       (map #(eval-spec % namespace))
       (filter :match?)))

(defn flag-answers!
  [db user instrument answers-map]
  (let [instrument-specs (project-instrument-specs
                           (flagging-specs db)
                           (:project-id user)
                           instrument)
        item-answers     (instrument-answers/merge-items-answers
                           instrument
                           answers-map)
        namespace        (namespace-map item-answers)]
    (let [matches (apply-instrument-specs instrument-specs namespace)]
      (doseq [match matches]
        (orm/create-flag! (:user-id user)
                          "AnswersFlagger"
                          (str "Answers on instrument " (:name instrument) " flagged. Reason: " (:message match))
                          {"CustomIcon"  "flag-high.gif"
                           "ReferenceId" (:answers-id answers-map)})))))
