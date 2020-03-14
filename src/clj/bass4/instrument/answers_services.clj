(ns bass4.instrument.answers-services
  (:require [bass4.db.core :as db]
            [clj-time.core :as t]
            [bass4.utils :refer [key-map-list map-map indices fnil+]]
            [bass4.php_clj.core :refer [clj->php]]
            [bass4.php-clj.safe :refer [php->clj]]
            [clojure.tools.logging :as log]
            [bass4.db.orm-classes :as orm]))

;; ------------------------------
;;    CREATE MISSING ANSWERS
;; ------------------------------

(defn- create-answers-object!
  []
  (orm/create-bass-objects-without-parent! "cInstrumentAnswers"
                                           "InstrumentAnswers"
                                           1))

(defn- delete-answers!
  [answers-id]
  (log/info "Deleting surplus answers " answers-id)
  (db/delete-object-from-objectlist! {:object-id answers-id})
  (db/delete-instrument-answers! {:answers-id answers-id}))

(defn- update-created-answers!
  "Points answers to administration and instrument and gracefully handles race conditions"
  [answers-id administration-id instrument-id]
  (try
    (db/create-instrument-answers! {:answers-id answers-id :administration-id administration-id :instrument-id instrument-id})
    (db/update-objectlist-parent! {:object-id answers-id :parent-id administration-id})
    (db/link-property-reverse! {:linkee-id instrument-id :property-name "Instrument" :linker-class "cInstrumentAnswers"})
    answers-id
    (catch Exception e (delete-answers! answers-id)
                       (:answers-id (db/get-instrument-answers-by-administration {:administration-id administration-id :instrument-id instrument-id})))))

(defn create-answers!
  [administration-id instrument-id]
  (-> (create-answers-object!)
      (first)
      (update-created-answers! administration-id instrument-id)))

(defn- instrument-answers-id
  [administration-id instrument-id]
  (let [answers (db/get-instrument-answers-by-administration {:administration-id administration-id :instrument-id instrument-id})]
    (if-not (seq answers)
      (create-answers! administration-id instrument-id)
      (:answers-id answers))))


;; ------------------------------
;;         SAVE ANSWERS
;; ------------------------------

(defn save-answers!
  ([answers-id answers-map]
   (save-answers! answers-id answers-map nil))
  ([answers-id {:keys [items specifications sums]} copy-of]
   (db/save-instrument-answers!
     {:answers-id     answers-id
      :items          (clj->php items)
      :specifications (clj->php specifications)
      :sums           (clj->php sums)
      :copy-of        copy-of})
   (when copy-of
     (db/link-property-reverse! {:linkee-id answers-id :property-name "CopyOf" :linker-class "cInstrumentAnswers"}))))

(defn save-administrations-answers!
  [administration-ids instrument-id answers-map]
  ;; TODO: Allocates them one by one - instrument-answers-id would have to be rewritten if unwanted
  (let [answers-ids (map #(instrument-answers-id % instrument-id) administration-ids)
        copy-ofs    (if (> (count answers-ids) 1)
                      (cons (second answers-ids) (repeat (dec (count answers-ids)) (first answers-ids)))
                      '(nil))]
    (mapv #(save-answers! %1 answers-map %2) answers-ids copy-ofs)
    (first answers-ids)))

(defn get-answers
  [administration-id instrument-id]
  (let [answers (db/get-instrument-answers-by-administration {:administration-id administration-id :instrument-id instrument-id})]
    (merge answers
           {:items          (php->clj (:items answers))
            :specifications (php->clj (:specifications answers))
            :sums           (php->clj (:sums answers))})))


;; ------------------------------
;;     ITEMS INTO NAMESPACE
;; ------------------------------

(defn- checkboxize
  "Makes checkbox items into one item per checkbox option."
  [instrument]
  (let [items (->> instrument
                   (:elements)
                   (filter :item-id))]
    (reduce (fn [coll item]
              (let [response (get (:responses instrument) (:response-id item))
                    res      (if (= "CB" (:response-type response))
                               (map #(merge
                                       {:item-id     (:item-id item)
                                        :checkbox-id (str (:item-id item) "_" (:value %))
                                        :name        (str (:name item) "_" (:value %))} %)
                                    (:options response))
                               (list item))]
                (concat coll res)))
            ()
            items)))

(defn merge-items-answers
  [instrument answers]
  (let [items (checkboxize instrument)]
    (when items
      (let [item-answers   (->> answers
                                :items
                                (map #(vector (str (first %)) (second %)))
                                (into {}))
            specifications (into {} (:specifications answers))]
        (assoc answers
          :specifications specifications
          :items
          (map
            (fn [item]
              (let [value (get item-answers (str (or (:checkbox-id item) (:item-id item))))]
                (merge
                  item
                  {:value         value
                   :specification (get specifications (or
                                                        (:checkbox-id item)
                                                        (str (:item-id item) "_" value)))})))
            items))))))
