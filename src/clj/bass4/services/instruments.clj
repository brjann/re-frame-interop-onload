(ns bass4.services.instruments
  (:require [bass4.db.core :as db]
            [bass4.php_clj.core :refer [php->clj]]
            [bass4.services.bass :refer [unserialize-key map-map]]))

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

(defn item-elements
  [bass-elements]
  (let [item-ids (mapv :item-id bass-elements)]
    (map (fn [bass-element]
           (let [current-id (:item-id bass-element)]
             (unserialize-key
               (select-keys bass-element
                            [:item-id :name :text :response-id :sort-order :layout-id :option-jumps])
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

(defn cells
  [{:keys [cell-widths cell-alignments]}]
  (map
    (fn [x y] {:cell-width x :cell-alignment y})
    (mapv
      #(if (or (empty? %) (= "0" %)) "*" %)
      (php->clj cell-widths))
    (php->clj cell-alignments)))

(defn table-elements
  [instrument-id]
  (map (fn [x] {:page-break (:page-break x) :cells (cells x) :sort-order (:sort-order x)})
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
  (select-keys bass-element [:layout-id :layout]))

(defn instrument-elements-and-responses
  [instrument-id]
  (let [bass-elements (db/get-instrument-items {:instrument-id instrument-id})
        items         #_(map item-elements bass-elements)
                      (item-elements bass-elements)
        responses     (key-map-list
                        (map response-def (filter :response-type bass-elements))
                        :response-id)
        layouts       (key-map-list
                        (map layout-def (filter :layout bass-elements))
                        :layout-id)
        statics       (db/get-instrument-statics {:instrument-id instrument-id})
        tables        (table-elements instrument-id)
        elements      (map #(dissoc %1 :sort-order) (sort-by :sort-order (concat items statics tables)))]
    {:elements elements
     :responses responses
     :layouts layouts}
    ))

(defn instrument-def
  [instrument-id]
  (let [instrument (db/get-instrument {:instrument-id instrument-id})
        {:keys [elements responses layouts]} (instrument-elements-and-responses instrument-id)]
    (when instrument
      (merge instrument
             {:elements elements
              :responses responses
              :layouts layouts})
      #_{:name (:name instrument)
       :abbreviation (:abbreviation instrument)
       :show-name (:show-name instrument)
       :elements elements
       :responses responses
       :layouts layouts})))

(defn get-instrument [instrument-id]
  (let [instrument (instrument-def instrument-id)]
    instrument))


#_(defn get-instrument-bar
    [instrument-id]
    {:title "PHQ-9" :abbreviation "PHQ-9"
     :items [
             {:text "<em>Detta frågeformulär är viktigt för att kunna ge dig bästa möjliga hälsovård. Dina svar kommer att underlätta förståelsen för problem som du kan  ha.</em>"}
             {:name "1" :text "Lite intresse eller glädje i att göra saker" :response {:type "RD"
                                                                                       :options [
                                                                                                 {:value 0 :label "0. Inte alls"}
                                                                                                 {:value 1 :label "1. Flera dagar"}
                                                                                                 {:value 2 :label "2. Mer än hälften av dagarna"}
                                                                                                 {:value 3 :label "3. Nästan varje dag"}]}}
             {:name "2" :text "Känt dig nedstämd, deprimerad, eller känt att framtiden ser hopplös ut." :response {:type "RD"
                                                                                                                   :options [
                                                                                                                             {:value 0 :label "0. Inte alls"}
                                                                                                                             {:value 1 :label "1. Flera dagar"}
                                                                                                                             {:value 2 :label "2. Mer än hälften av dagarna"}
                                                                                                                             {:value 3 :label "3. Nästan varje dag"}]}}]})

