(ns bass4.responses.instrument
  (:require [bass4.services.instrument :as instruments]
            [ring.util.http-response :as response]
            [bass4.utils :refer [json-safe]]
            [clojure.tools.logging :as log]))

(defn instrument-page [instrument-id]
  (bass4.layout/render "instrument.html" {:instrument (instruments/get-instrument instrument-id) :instrument-id instrument-id}))

;; TODO: Add input spec
(defn post-answers [instrument-id items-str specifications-str]
  (if-let [instrument (instruments/get-instrument instrument-id)]
    (let [items (json-safe items-str)
          specifications (json-safe specifications-str)]
      (if (or (nil? items) (nil? specifications))
        (bass4.layout/error-400-page)
        (let [item-names (map #(select-keys % [:item-id :name]) (filter :response-id (:elements instrument)))
              sums (instruments/score-items items (instruments/get-scoring instrument-id))]
          (instruments/save-test-answers! instrument-id items specifications sums item-names)
          (bass4.layout/render
            "instrument-answers.html"
            {:items items :specifications specifications :sums sums}))))))