(ns bass4.responses.instrument
  (:require [bass4.services.instrument :as instruments]
            [ring.util.http-response :as response]
            [clojure.data.json :as json]))

;; TODO: Add input spec
(defn instrument-page [instrument-id]
  (bass4.layout/render "instrument.html" {:instrument (instruments/get-instrument instrument-id) :instrument-id instrument-id}))

;; TODO: Add input spec
;; TODO: Handle exception if answers cannot be parsed as JSON
(defn post-answers [instrument-id items specifications]
  (let [items-map (json/read-str items)
        specifications-map (json/read-str specifications)
        instrument (instruments/get-instrument instrument-id)
        item-names (map #(select-keys % [:item-id :name]) (filter :response-id (:elements instrument)))]
    (bass4.layout/render "instrument-answers.html" {:items items-map :specifications specifications-map})))