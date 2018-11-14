(ns bass4.instruments.validation
  (:require [bass4.php_clj.core :refer [clj->php]]
            [bass4.php-clj.safe :refer [php->clj]]
            [bass4.services.instrument :as instruments-service]
            [bass4.utils :refer [unserialize-key map-map subs+ keep-matching key-map-list json-safe]]
            [clojure.string :as s]
            [clojure.tools.logging :as log]
            [bass4.utils :as utils]
            [clojure.set :as set]))


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

(defn- merge-answers
  [items-map item-answers]
  (utils/map-map-keys
    (fn [item item-id]
      (let [answer-map (if (= "CB" (:response-type item))
                         (select-keys item-answers (map #(checkbox-id item-id %) (keys (:options item))))
                         {(str item-id) (get item-answers (str item-id))})]
        (assoc
          item
          :answer
          (into {} (filter second answer-map)))))
    items-map))

;; ------------------------
;;          JUMPS
;; ------------------------


(defn- get-jump-map
  [items]
  (->> items
       (reduce (fn [coll [item-id item]]
                 (let [res (case (:response-type item)
                             "CB"
                             (map (fn [[value option]]
                                    [(checkbox-id item-id value)
                                     {"1" (:jump option)}])
                                  (:options item))

                             "RD"
                             (list [(str item-id)
                                    (into {} (map (fn [[value option]]
                                                    [value (:jump option)])
                                                  (:options item)))])

                             nil)]
                   (concat coll res)))
               ())
       (into {})))

(defn- get-jumped-items
  [items-map item-answers]
  (let [jump-map     (get-jump-map items-map)
        jumped-items (->> item-answers
                          (keep (fn [[item-id answer]]
                                  (get-in jump-map [item-id answer])))
                          (flatten)
                          (into #{}))]
    jumped-items))

(defn- skipped-jumps
  [jumped-items items+answers]
  (let [items-with-answers (->> items+answers
                                (keep (fn [[item-id item+answer]]
                                        (and (not-empty (:answer item+answer)) item-id)))
                                (into #{}))
        overlap            (set/intersection jumped-items items-with-answers)]
    (when (seq overlap)
      {:jumps overlap})))

;; ------------------------
;;      MISSING ITEMS
;; ------------------------

(defn- missing-items
  [items+answers jumped-items]
  (let [items-with-answers (->> items+answers
                                (keep (fn [[item-id item+answer]]
                                        (if (= "CB" (:response-type item+answer))
                                          (when (some #(= "1" %) (vals (:answer item+answer)))
                                            item-id)
                                          (when (seq (:answer item+answer))
                                            item-id))))
                                (into #{}))
        mandatory          (->> items+answers
                                (filter (fn [[_ item]] (not (:optional? item))))
                                (keys)
                                (into #{}))
        missing            (set/difference mandatory items-with-answers jumped-items)]
    (when (seq missing)
      {:missing missing})))


;; ------------------------
;;       CONSTRAINTS
;; ------------------------

(defmulti check-constraints (fn [[item-id item]] (keyword (str *ns*) (:response-type item))))
(derive ::ST ::text)
(derive ::TX ::text)

(defmethod check-constraints ::text
  [item+answer])

(defmethod check-constraints ::CB
  [[item-id item+answer]]
  (let [option-keys  (->> (:options item+answer)
                          (keys)
                          (map #(checkbox-id item-id %))
                          (into #{}))
        present-keys (->>
                       (:answer item+answer)
                       (keys)
                       (into #{}))
        missing-keys (set/difference option-keys present-keys)
        not-binary   (keep (fn [[answer-id answer]]
                             (when
                               (not (contains? #{"0" "1"} answer))
                               answer-id))
                           (:answer item+answer))]
    (when (or (seq missing-keys)
              (seq not-binary))
      {item-id (merge
                 (when (seq missing-keys)
                   {:missing-checkboxes missing-keys})
                 (when (seq not-binary)
                   {:not-binary-checkbox not-binary}))})))

(defmethod check-constraints :default
  [item+answer])


(defn check-item-constraints
  [items+answers jumped-items]
  (let [items+answers (apply dissoc items+answers jumped-items)
        constraints   (keep check-constraints items+answers)]
    (when (seq constraints)
      {:constraints constraints})))


;; TODO: Superfluous?

(defn validate-answers*
  [items-map item-answers specifications]
  (let [jumped-items  (get-jumped-items items-map item-answers)
        items+answers (->> item-answers
                           (utils/filter-map #(not (empty? %)))
                           (merge-answers items-map))]
    (merge
      (skipped-jumps jumped-items items+answers)
      (missing-items items+answers jumped-items)
      (check-item-constraints items+answers jumped-items))))


(defn validate-answers
  [instrument-id item-answers specifications]
  (if-let [instrument (instruments-service/get-instrument instrument-id)]
    (validate-answers* (get-items-map instrument) item-answers specifications)
    :error))
