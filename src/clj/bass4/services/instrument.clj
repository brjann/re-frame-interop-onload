(ns bass4.services.instrument
  (:require [bass4.db.core :as db]
            [bass4.php_clj.core :refer [clj->php]]
            [bass4.php-clj.safe :refer [php->clj]]
            [bass4.utils :refer [unserialize-key map-map subs+ keep-matching key-map-list json-safe]]
            [bass4.infix-parser :as infix]
            [bass4.services.instrument-answers :as instrument-answers]
            [clojure.string :as s]
            [clojure.tools.logging :as log]))


;; ------------------------
;;    INSTRUMENT PARSER
;; ------------------------

(defn jumper-fn
  [item-ids current-id]
  (fn [jump-to]
    (subvec item-ids
            (+ 1 (first (keep-indexed #(when (= current-id %2) %1) item-ids)))
            (first (keep-indexed #(when (= jump-to %2) %1) item-ids)))))

(defn process-item-elements
  [item-elements]
  (let [item-ids (mapv :item-id item-elements)
        ;; A jump-map is returned as {} rather than []
        ;; if the keys in the php array are not sorted
        ;; or some intermediate keys are missing
        ;; This function returns a sorted seq from a map
        unmap    (fn [m]
                   (if (map? m)
                     (->> m
                          ;; Make sure all keys between 0 and 19 are present
                          (merge (zipmap (range 0 20) (repeat 20 0)))
                          (into [])
                          (sort #(compare (first %1) (first %2)))
                          (map second))
                     m))]
    (map (fn [item]
           (let [current-id (:item-id item)]
             (unserialize-key
               (select-keys item
                            [:item-id :name :text :response-id :sort-order :layout-id :option-jumps :page-break :optional])
               :option-jumps
               (fn [m] (map-map
                         (jumper-fn item-ids current-id)
                         (keep-matching #(< 0 %) (unmap m)))))))
         item-elements)))

(defn make-option
  [value label specification-text specification-big?]
  (let [specification? (not (empty? specification-text))]
    {:value              value
     :label              label
     :specification      specification?
     :specification-text (if specification? specification-text nil)
     :specification-big  (if specification? specification-big? nil)}))

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
    {:elements       elements
     :responses      responses
     :layouts        layouts
     :static-layouts static-layouts}))

(defn instrument-def
  [instrument-id]
  (when-let [instrument (db/get-instrument {:instrument-id instrument-id})]
    (merge instrument
           (instrument-elements-and-responses instrument-id))))

(defn get-instrument [instrument-id]
  (let [instrument (instrument-def instrument-id)]
    instrument))

(defn get-instrument-test-answers [instrument-id]
  (instrument-answers/get-answers instrument-id instrument-id))

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

(defn- scoring-parse-statement
  [statement-line]
  (let [[var expression] (mapv s/trim (s/split statement-line #"=" 2))]
    (when (and (> (count var) 0) (> (count expression) 0))
      (if (= (subs+ expression 0 2) "if")
        (let [if-expr (s/split (subs expression 2) #",")]
          (when (= (count if-expr) 3)
            {:var   var
             :test  (if-expr 0)
             :true  (if-expr 1)
             :false (if-expr 2)}))
        {:var var :expression expression}))))

(defn- scoring-statements
  [lines]
  (when-let [statement-lines (filterv #(not= (subs % 0 1) "#") lines)]
    statement-lines))

(defn- parse-scoring
  [scoring-string]
  (let [lines   (filterv #(not= (count %) 0) (mapv (comp s/lower-case s/trim) (s/split-lines scoring-string)))
        exports (scoring-exports lines)]
    (when exports
      {:statements (remove nil? (mapv scoring-parse-statement (scoring-statements lines)))
       :exports    exports}
      )))

(defn get-scoring
  [instrument-id]
  (let [{scoring-string :scoring default-value :default-value} (db/get-instrument-scoring {:instrument-id instrument-id})]
    (when (> (count scoring-string) 0)
      (assoc (parse-scoring scoring-string) :default-value (or default-value 0)))))


;; ------------------------
;;     ANSWERS SCORING
;; ------------------------


;; Returns 0 in case of exception or nil result
(defn- expression-resolver
  [default-value]
  (fn
    [expression namespace]
    (or (try
          (infix/calc
            expression
            (infix/token-resolver
              namespace
              (fn [token]
                ;; In BASS, missing items are scored as default-value
                ;; and missing variables are scored as 0
                (if (= "$" (subs token 0 1))
                  default-value
                  0))))
          (catch Exception e 0)) 0)))

(defn- statement-resolver
  [default-value]
  (let [expression-resolver-fn (expression-resolver default-value)]
    (fn
      [namespace statement]
      (let [var (:var statement)
            val (if-let [test (:test statement)]
                  (if (not= (expression-resolver-fn test namespace) 0)
                    (expression-resolver-fn (:true statement) namespace)
                    (expression-resolver-fn (:false statement) namespace))
                  (expression-resolver-fn (:expression statement) namespace))]
        (assoc namespace var val)))))

;; TODO: Verify correct instrument answers have been posted
(defn score-items
  [items {:keys [statements default-value exports]}]
  (let [$items (zipmap (map #(str "$" %) (keys items)) (vals items))]
    (select-keys
      (reduce (statement-resolver default-value) $items statements)
      exports)))

(defn score-instrument
  [items instrument-id]
  (if-let [scoring (get-scoring instrument-id)]
    (score-items items scoring)))

(defn save-test-answers!
  [instrument-id answers-map]
  (when-let [answers-id (:answers-id (instrument-answers/get-answers instrument-id instrument-id))]
    (instrument-answers/save-answers! answers-id answers-map)))

(defn parse-answers-post
  [instrument-id items-str specifications-str]
  (let [instrument     (get-instrument instrument-id)
        items          (json-safe items-str)
        specifications (json-safe specifications-str)]
    (if (or (nil? items) (nil? specifications) (nil? instrument))
      nil
      {:items          items
       :specifications specifications
       :sums           (score-items items (get-scoring instrument-id))})))