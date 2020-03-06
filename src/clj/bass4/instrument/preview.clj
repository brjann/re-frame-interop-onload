(ns bass4.instrument.preview
  (:require [bass4.instrument.services :as instruments]
            [ring.util.http-response :as http-response]
            [bass4.utils :refer [map-map str->int]]
            [bass4.layout :as layout]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.instrument.validation :as validation]
            [bass4.middleware.request-logger :as request-logger]))

(defapi instrument-page
  [instrument-id :- api/->int]
  (if-let [instrument (instruments/get-instrument instrument-id)]
    (layout/render "instrument-preview.html" {:instrument instrument :instrument-id instrument-id})
    (http-response/not-found)))

(defapi post-answers
  [instrument-id :- api/->int items :- [api/->json map?] specifications :- [api/->json map?]]
  (if-let [instrument (instruments/get-instrument instrument-id)]
    (do
      (validation/validate-answers instrument items specifications)
      (let [answers-map {:items          items
                         :specifications specifications
                         :sums           (instruments/score-items items (instruments/get-scoring instrument-id))}]
        (instruments/save-test-answers! instrument-id answers-map)
        (http-response/found (str "/embedded/instrument/" instrument-id "/summary"))))
    (do
      (request-logger/record-error! (str "Instrument " instrument-id " does not exist"))
      (http-response/bad-request))))

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

(defn- merge-items-answers
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

(defapi summary-page
  [instrument-id :- api/->int]
  (let [answers (merge-items-answers
                  (instruments/get-instrument instrument-id)
                  (instruments/get-instrument-test-answers instrument-id))]
    (when (:items answers)
      (bass4.layout/render
        "instrument-answers.html"
        {:items (:items answers)
         :sums  (:sums answers)}))))