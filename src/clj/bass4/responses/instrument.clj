(ns bass4.responses.instrument
  (:require [bass4.services.instrument :as instruments]
            [ring.util.http-response :as response]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]))

;; TODO: Add input spec
(defn instrument-page [instrument-id]
  (bass4.layout/render "instrument.html" {:instrument (instruments/get-instrument instrument-id) :instrument-id instrument-id}))

;; TODO: Add input spec
;; TODO: Handle exception if answers cannot be parsed as JSON
(defn post-answers [instrument-id items-str specifications-str]
  (if-let [instrument (instruments/get-instrument instrument-id)]
    (let [items (json/read-str items-str)
          specifications (json/read-str specifications-str)
          item-names (map #(select-keys % [:item-id :name]) (filter :response-id (:elements instrument)))
          sums (instruments/score-items items (instruments/get-scoring instrument-id))]
      (bass4.layout/render "instrument-answers.html" {:items items :specifications specifications :sums sums}))))