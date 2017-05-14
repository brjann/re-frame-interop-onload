(ns bass4.services.instrument
  (:require [bass4.db.core :as db]
            [bass4.php_clj.core :refer [php->clj]]
            [bass4.services.bass :refer [unserialize-key map-map subs+]]
            [clojure.string :as s]))


;; ------------------------
;;    INSTRUMENT BUILDER
;; ------------------------

;; TODO: Should be able to use select-in instead of filter if matching keys are saved.
(defn keep-matching
  [f m]
  (zipmap (keep-indexed #(when (f %2) %1) m) (filter f m)))

(defn key-map-list
  ([s k]
   (key-map-list s k {}))
  ([s k m]
   (if-not (seq s)
     m
     (recur (rest s)
            k
            (assoc m (get (first s) k) (first s))))))

(defn jumper-fn
  [item-ids current-id]
  (fn [jump-to]
    (subvec item-ids
            (+ 1 (first (keep-indexed #(when(= current-id %2) %1) item-ids)))
            (first (keep-indexed #(when(= jump-to %2) %1) item-ids)))))

(defn process-item-elements
  [bass-elements]
  (let [item-ids (mapv :item-id bass-elements)]
    (map (fn [bass-element]
           (let [current-id (:item-id bass-element)]
             (unserialize-key
               (select-keys bass-element
                            [:item-id :name :text :response-id :sort-order :layout-id :option-jumps :page-break :optional])
               :option-jumps
               (fn [m] (map-map
                         (jumper-fn item-ids current-id)
                         (keep-matching #(< 0 %) m))))))
         bass-elements)))

(defn make-option
  [value label specification-text specification-big?]
  (let [specification? (not (empty? specification-text))]
    {:value value
     :label label
     :specification specification?
     :specification-text (if specification? specification-text nil)
     :specification-big (if specification? specification-big? nil)}))

(defn options
  [{:keys [option-values option-labels option-specifications option-specifications-big option-jumps]}]
  (filter (comp (complement empty?) :value)
          (map make-option
               (php->clj option-values)
               (php->clj option-labels)
               (php->clj option-specifications)
               (php->clj option-specifications-big))))

(defn cols
  [{:keys [col-widths col-alignments]}]
  (map
    (fn [x y] {:col-width x :col-alignment y})
    (mapv
      #(if (or (empty? %) (= "0" %)) "*" %)
      (php->clj col-widths))
    (php->clj col-alignments)))

(defn table-elements
  [instrument-id]
  (map (fn [x] {:page-break (:page-break x) :cols (cols x) :sort-order (:sort-order x)})
       (db/get-instrument-tables {:instrument-id instrument-id})))

(defn response-def
  [bass-element]
  (-> bass-element
      ;; TODO: Only select relevant keys depending on response-type
      (select-keys [:response-type :option-separator :vas-min-label :vas-max-label :range-min :range-max :check-error-text :regexp])
      (assoc :options (options bass-element))
      (assoc :response-id (:item-id bass-element))))

(defn layout-def
  [bass-element]
  (select-keys bass-element [:layout-id :layout :border-bottom-width :border-top-width]))

(defn layout-map
  [elements]
  (key-map-list
    (map layout-def (filter :layout elements))
    :layout-id))

(defn instrument-elements-and-responses
  [instrument-id]
  (let [item-elements   (db/get-instrument-items {:instrument-id instrument-id})
        items           (process-item-elements item-elements)
        responses       (key-map-list
                          (map response-def (filter :response-type item-elements))
                          :response-id)
        layouts         (layout-map item-elements)
        static-elements (db/get-instrument-statics {:instrument-id instrument-id})
        statics         (mapv #(select-keys % [:text :name :layout-id :sort-order :page-break]) static-elements)
        static-layouts  (layout-map static-elements)
        tables          (table-elements instrument-id)
        elements        (map #(dissoc %1 :sort-order) (sort-by :sort-order (concat items statics tables)))]
    {:elements elements
     :responses responses
     :layouts layouts
     :static-layouts static-layouts}))

(defn instrument-def
  [instrument-id]
  (when-let [instrument (db/get-instrument {:instrument-id instrument-id})]
    (merge instrument
           (instrument-elements-and-responses instrument-id))))

(defn get-instrument [instrument-id]
  (let [instrument (instrument-def instrument-id)]
    instrument))



;; ------------------------
;;     SCORING PARSER
;; ------------------------

(defn- scoring-exports
  [lines]
  (when-let [sums-line (first (filterv #(= (subs+ % 0 5) "#sums") lines))]
    (-> sums-line
        (subs 5)
        (s/replace #" " "")
        (s/split #","))))

(defn- scoring-parse-expression
  [expression-line]
  (let [[var expression] (mapv s/trim (s/split expression-line #"=" 2))]
    (when (and (> (count var) 0) (> (count expression) 0))
      (if (= (subs+ expression 0 2) "if")
        (let [if-expr (s/split (subs expression 2) #",")]
          (when (= (count if-expr) 3)
            {:var  var
             :test (if-expr 0)
             :true (if-expr 1)
             :false (if-expr 2)}))
        {:var var :expression expression}))))

(defn- scoring-expressions
  [lines]
  (when-let [expression-lines (filterv #(not= (subs % 0 1) "#") lines)]
    expression-lines))

(defn- parse-scoring
  [scoring-string]
  (let [lines (filterv #(not= (count %) 0) (mapv (comp s/lower-case s/trim) (s/split-lines scoring-string)))
        exports (scoring-exports lines)]
    (when exports
      {:expressions (remove nil? (mapv scoring-parse-expression (scoring-expressions lines)))
       :exports exports}
      )))

(defn get-instrument-scoring
  [instrument-id]
  (when-let [scoring-string (:scoring (db/get-instrument-scoring {:instrument-id instrument-id}))]
    (parse-scoring scoring-string)))