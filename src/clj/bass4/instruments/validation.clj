(ns bass4.instruments.validation
  (:require [bass4.php_clj.core :refer [clj->php]]
            [bass4.php-clj.safe :refer [php->clj]]
            [bass4.services.instrument :as instruments-service]
            [bass4.utils :refer [unserialize-key map-map subs+ keep-matching key-map-list json-safe]]
            [clojure.string :as s]
            [clojure.tools.logging :as log]
            [bass4.utils :as utils]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.pprint :as pprint]))


(defn- checkbox-id
  [item-id value]
  (str item-id "_" value))

;; ------------------------
;;         ITEM MAP
;; ------------------------

(defn get-items-map
  [instrument]
  (let [merge-jumps (fn [item]
                      (if (contains? #{"RD" "CB"} (:response-type item))
                        (assoc
                          item
                          :options
                          (map-indexed
                            (fn [index option]
                              (assoc option :jump (get (:option-jumps item) index)))
                            (:options item)))
                        item))
        map-options (fn [item]
                      (if (contains? #{"RD" "CB"} (:response-type item))
                        (assoc
                          item
                          :options
                          (into {} (map #(vector (:value %) %) (:options item))))
                        item))
        items       (->> instrument
                         (:elements)
                         (filter :item-id)
                         (map (fn [item]
                                (let [response-def (get (:responses instrument)
                                                        (:response-id item))
                                      layout-def   (get-in (:layouts instrument)
                                                           [(:layout-id item) :layout])]
                                  (when (or (nil? response-def) (nil? layout-def))
                                    (throw (ex-info "Item does not have response or layout def" item)))
                                  (let [item (->> item
                                                  (merge response-def {:layout layout-def})
                                                  (merge-jumps)
                                                  (map-options))]
                                    item)))))]
    (->> items
         (filter #(s/includes? (:layout %) "[X]"))
         (map (fn [item] [(:item-id item) item]))
         (into {}))))

(defn merge-answers
  [items-map item-answers]
  (utils/map-map-keys
    (fn [item item-id]
      (let [non-empty  (utils/filter-map #(not (empty? %)) item-answers)
            answer-map (if (= "CB" (:response-type item))
                         (->> (keys (:options item))
                              (map #(checkbox-id item-id %))
                              (map #(get non-empty %))
                              (zipmap (keys (:options item))))
                         {item-id (get non-empty (str item-id))})]
        (assoc
          item
          :answer
          (into {} (filter second answer-map)))))
    items-map))

(defn get-items-with-answers
  [items+answers]
  (->> items+answers
       (keep (fn [[item-id item+answer]]
               (if (= "CB" (:response-type item+answer))
                 (when (not (every? #(contains? #{"" "0"} %) (vals (:answer item+answer))))
                   item-id)
                 (when (seq (:answer item+answer))
                   item-id))))
       (into #{})))


;; ------------------------
;;          JUMPS
;; ------------------------


#_(defn- get-jump-spec-map
    [items option-key]
    (->> items
         (reduce (fn [coll [item-id item]]
                   (let [res (case (:response-type item)
                               "CB"
                               (map (fn [[value option]]
                                      [(checkbox-id item-id value)
                                       {"1" (get option option-key)}])
                                    (:options item))

                               "RD"
                               (list [(str item-id)
                                      (into {} (map (fn [[value option]]
                                                      [value (get option option-key)])
                                                    (:options item)))])

                               nil)]
                     (concat coll res)))
                 ())
         (into {})))

#_(defn- get-jump-spec-map
    [items option-key]
    (->> items
         (reduce (fn [coll [item-id item]]
                   (let [res (case (:response-type item)
                               "CB"
                               (map (fn [[value option]]
                                      [[[item-id :answer value] "1"]
                                       (get option option-key)])
                                    (:options item))

                               "RD"
                               (list [(str item-id)
                                      (into {} (map (fn [[value option]]
                                                      [[[item-id :answer item-id] value]
                                                       (get option option-key)])
                                                    (:options item)))])

                               nil)]
                     (concat coll res)))
                 ())
         (into {})
         (utils/filter-map identity)))

#_(defn- get-jumped-items
    [items-map item-answers]
    (let [jump-map     (get-jump-spec-map items-map :jump)
          jumped-items (->> item-answers
                            (keep (fn [[item-id answer]]
                                    (get-in jump-map [item-id answer])))
                            (flatten)
                            (into #{}))]
      jumped-items))

#_(defn- get-jumped-items
    [item+answers]
    (let [jump-map     (get-jump-spec-map items+map :jump)
          jumped-items (->> item-answers
                            (keep (fn [[item-id answer]]
                                    (get-in jump-map [item-id answer])))
                            (flatten)
                            (into #{}))]
      jumped-items))

(defn- get-expected-jumps
  [items+answers]
  (->> items+answers
       (keep (fn [[item-id item]]
               (case (:response-type item)
                 "CB"
                 (keep (fn [[value answer]]
                         (when (= "1" answer)
                           (get-in (:options item) [value :jump])))
                       (:answer item))

                 "RD"
                 (get-in (:options item) [(first (vals (:answer item))) :jump])

                 nil)))
       (flatten)
       (into #{})))

;; ------------------------
;;     SPECIFICATIONS
;; ------------------------

#_(defn- get-answers-missing-spec
    [items-map item-answers specifications]
    (let [spec-map     (get-jump-spec-map items-map :specification?)
          spec-items   (keep (fn [[item-id answer]]
                               (when (get-in spec-map [item-id answer])
                                 (if (str/includes? item-id "_")
                                   item-id
                                   (str item-id "_" answer))))
                             item-answers)
          spec-missing (->> spec-items
                            (filter (fn [item]
                                      (empty? (get specifications item))))
                            (into #{}))]
      spec-missing))

(defn- get-expected-specs
  [items+answers]
  (->> items+answers
       (keep (fn [[item-id item]]
               (case (:response-type item)
                 "CB"
                 (keep (fn [[value answer]]
                         (when (and (= "1" answer)
                                    (get-in (:options item) [value :specification?]))
                           (checkbox-id item-id value)))
                       (:answer item))

                 "RD"
                 (let [answer (first (vals (:answer item)))]
                   (when (get-in (:options item) [answer :specification?])
                     (str item-id "_" answer)))

                 nil)))
       (flatten)
       (into #{})))

(defn- get-specs-with-answers
  [specifications]
  (->> specifications
       (keep (fn [[spec-id answer]]
               (when-not (empty? answer)
                 spec-id)))
       (into #{})))

;; ------------------------
;;      MISSING ITEMS
;; ------------------------


(defn- get-mandatory-items
  [items-map]
  (->> items-map
       (filter (fn [[_ item]] (not (:optional? item))))
       (keys)
       (into #{})))

;; ------------------------
;;       CONSTRAINTS
;; ------------------------

(let [ns* (str *ns*)]
  ;; Must fix ns* because dispatch gets called in current namespace or something
  (defmulti check-constraints (fn [[item-id item]] (keyword ns* (:response-type item)))))
(derive ::ST ::text)
(derive ::TX ::text)

(defmethod check-constraints ::text
  [[item-id item+answer]]
  (let [answer       (-> (:answer item+answer)
                         (vals)
                         (first))
        range-error? (when (or (:range-max item+answer) (:range-min item+answer))
                       (let [answer-int (utils/str->int answer)
                             range-max  (:range-max item+answer)
                             range-min  (:range-min item+answer)]
                         (cond
                           (nil? answer-int)
                           true

                           (and range-min range-max)
                           (when-not (and (<= range-min answer-int)
                                          (>= range-max answer-int))
                             true)

                           range-min
                           (when-not (<= range-min answer-int)
                             true)

                           range-max
                           (when-not (>= range-max answer-int)
                             true))))
        regex-error? (when-not (empty? (:regex item+answer))

                       (try
                         (let [regex (re-pattern (:regex item+answer))]
                           (when-not (re-matches regex answer)
                             true))
                         (catch Exception _)))]
    (when (or range-error?
              regex-error?)
      [item-id (merge
                 (when range-error?
                   {:range-error answer})
                 (when regex-error?
                   {:regex-error answer}))])))

(defmethod check-constraints ::CB
  [[item-id item+answer]]
  (let [option-keys  (->> (:options item+answer)
                          (keys)
                          (into #{}))
        present-keys (->>
                       (:answer item+answer)
                       (keys)
                       (into #{}))
        missing-keys (set/difference option-keys present-keys)
        not-binary   (->> (:answer item+answer)
                          (keep (fn [[answer-id answer]]
                                  (when
                                    (not (contains? #{"0" "1"} answer))
                                    answer-id)))
                          (into #{}))]
    (when (or (seq missing-keys)
              (seq not-binary))
      [item-id (merge
                 (when (seq missing-keys)
                   {:checkboxes-missing missing-keys})
                 (when (seq not-binary)
                   {:checkbox-not-binary not-binary}))])))

(defmethod check-constraints ::RD
  [[item-id item+answer]]
  (let [answer (-> (:answer item+answer)
                   (vals)
                   (first))]
    (when-not (contains? (:options item+answer) answer)
      [item-id {:radio-invalid-value answer}])))

(defmethod check-constraints ::VS
  [[item-id item+answer]]
  (let [answer     (-> (:answer item+answer)
                       (vals)
                       (first))
        answer-int (utils/str->int answer)]
    (when (or (nil? answer-int) (not (and (<= 0 answer-int) (>= 400 answer-int))))
      [item-id {:vas-invalid answer}])))


;; TODO: Superfluous?

(defn validate-answers*
  [items-map item-answers specifications]
  (let [mandatory-items    (get-mandatory-items items-map)

        items+answers      (merge-answers items-map item-answers)
        items-with-answers (get-items-with-answers items+answers)
        specs-with-answers (get-specs-with-answers specifications)
        jumped-items       (get-expected-jumps items+answers)

        skipped-jumps      (set/intersection jumped-items items-with-answers)
        missing-items      (set/difference mandatory-items items-with-answers jumped-items)
        constrain-items    (select-keys items+answers (set/difference items-with-answers jumped-items missing-items))
        expected-specs     (get-expected-specs constrain-items)
        missing-specs      (set/difference expected-specs specs-with-answers)
        constraints        (keep check-constraints constrain-items)]
    (merge
      (when (seq skipped-jumps)
        {:jumps skipped-jumps})
      (when (seq missing-items)
        {:missing missing-items})
      (when (seq constraints)
        {:constraints (into {} constraints)})
      (when (seq missing-specs)
        {:missing-specs missing-specs}))))


(defn validate-answers
  [instrument-id item-answers specifications]
  (if-let [instrument (instruments-service/get-instrument instrument-id)]
    (validate-answers* (get-items-map instrument) item-answers specifications)
    :error))
