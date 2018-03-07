(ns bass4.responses.instrument
  (:require [bass4.services.instrument :as instruments]
            [ring.util.http-response :as response]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [bass4.utils :refer [map-map str->int]]
            [bass4.layout :as layout]
            [clojure.string :as string]
            [bass4.request-state :as request-state]))

(defn instrument-page [instrument-id]
  (if-let [instrument (instruments/get-instrument instrument-id)]
    (layout/render "instrument-preview.html" {:instrument instrument :instrument-id instrument-id})
    (layout/error-404-page)))

(s/defn ^:always-validate post-answers [instrument-id :- s/Int items-str :- s/Str specifications-str :- s/Str]
  (if-let [answers-map (instruments/parse-answers-post instrument-id items-str specifications-str)]
    (do
      (instruments/save-test-answers! instrument-id answers-map)
      (response/found (str "/embedded/instrument/" instrument-id "/summary")))
    (do
      (request-state/record-error! "Instrument post was not in valid JSON format")
      (layout/error-400-page))))


(defn- get-test-answers
  [instrument-id]
  (let [items (->> (instruments/get-instrument instrument-id)
                   (:elements)
                   (filter :item-id))]
    (when items
      (let [answers      (instruments/get-instrument-test-answers instrument-id)
            item-answers (into {} (:items answers))]
        (assoc answers :items (map #(assoc % :value (get item-answers (str (:item-id %)))) items))))))

(defn summary-page [instrument-id]
  (let [answers (get-test-answers instrument-id)]
    (when (:items answers)
      (bass4.layout/render
        "instrument-answers.html"
        {:items (:items answers) :specifications (:specifications answers) :sums (:sums answers)}))))


(def x
  (fn [[key specification]]
    (let [[item-id option] (string/split key #"_")] {:item-id (str->int item-id) :value option :specification specification})))

(defn checkboxize
  [instrument]
  (let [items (filter :item-id (:elements instrument))]
    (map (fn [item]
           (let [response (get (:responses instrument) (:response-id item))]
             (if (= "CB" (:response-type response))
               (map #(merge {:spec-id (str (:item-id item) "_" (:value %))} %) (:options response))
               item)))
         items)))