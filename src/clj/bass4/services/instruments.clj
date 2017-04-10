(ns bass4.services.instruments
  (:require [bass4.db.core :as db]
            [bass4.php_clj.core :refer [php->clj]]))

(defn item-elements
  [bass-element]
  (select-keys bass-element [:item-id :name :text :response-id :sort-order]))


(defn options
  [{:keys [option-values option-labels]}]
  (filter (comp (complement empty?) :value) (map (fn [x y] {:value x :label y}) (php->clj option-values) (php->clj option-labels))))

(defn response-def
  [bass-element]
  (-> bass-element
      (select-keys [:response-type])
      (assoc :options (options bass-element))
      (assoc :response-id (:item-id bass-element))))

(defn instrument-elements-and-responses
  [instrument-id]
  (let [bass-elements (db/get-instrument-items {:instrument-id instrument-id})
        items (map item-elements bass-elements)
        responses (map response-def (filter :option-values bass-elements))
        statics (db/get-instrument-statics {:instrument-id instrument-id})
        elements (map #(dissoc %1 :sort-order) (sort-by :sort-order (concat items statics)))]
    {:elements elements
     :responses responses}
    ))

(defn instrument-def
  [instrument-id]
  (let [instrument (db/get-instrument {:instrument-id instrument-id})
        {:keys [elements responses]} (instrument-elements-and-responses instrument-id)]
    (when instrument
      {:name (:name instrument)
       :abbreviation (:abbreviation instrument)
       :show-name (:show-name instrument)
       :elements elements
       :responses responses})))

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

