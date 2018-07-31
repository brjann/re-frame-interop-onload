(ns bass4.responses.instrument
  (:require [bass4.services.instrument :as instruments]
            [ring.util.http-response :as http-response]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [bass4.utils :refer [map-map str->int]]
            [bass4.layout :as layout]
            [bass4.request-state :as request-state]))

(defn instrument-page [instrument-id]
  (if-let [instrument (instruments/get-instrument instrument-id)]
    (layout/render "instrument-preview.html" {:instrument instrument :instrument-id instrument-id})
    (layout/error-404-page)))

(s/defn ^:always-validate post-answers [instrument-id :- s/Int items-str :- s/Str specifications-str :- s/Str]
  (if-let [answers-map (instruments/parse-answers-post instrument-id items-str specifications-str)]
    (do
      (instruments/save-test-answers! instrument-id answers-map)
      (http-response/found (str "/embedded/instrument/" instrument-id "/summary")))
    (do
      (request-state/record-error! "Instrument post was not in valid JSON format")
      (layout/error-400-page))))

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

(defn- get-test-answers
  [instrument-id]
  (let [items (checkboxize (instruments/get-instrument instrument-id))]
    (when items
      (let [answers        (instruments/get-instrument-test-answers instrument-id)
            item-answers   (->> answers
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

(defn summary-page [instrument-id]
  (let [answers (get-test-answers instrument-id)]
    (when (:items answers)
      (bass4.layout/render
        "instrument-answers.html"
        {:items (:items answers)
         :sums  (:sums answers)}))))