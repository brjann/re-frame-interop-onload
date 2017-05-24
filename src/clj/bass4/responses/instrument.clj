(ns bass4.responses.instrument
  (:require [bass4.services.instrument :as instruments]
            [ring.util.http-response :as response]
            [clojure.tools.logging :as log]))

(defn instrument-page [instrument-id]
  (bass4.layout/render "instrument.html" {:instrument (instruments/get-instrument instrument-id) :instrument-id instrument-id}))

#_(defn post-answers [instrument-id items-str specifications-str]
  (if-let [instrument (instruments/get-instrument instrument-id)]
    (let [items (json-safe items-str)
          specifications (json-safe specifications-str)]
      (if (or (nil? items) (nil? specifications))
        (bass4.layout/error-400-page)
        (let [sums (instruments/score-items items (instruments/get-scoring instrument-id))]
          (instruments/save-test-answers! instrument-id items specifications sums)
          (response/found (str "/instrument/summary/" instrument-id)))))))

;; TODO: Add input spec and return proper error
(defn post-answers [instrument-id items-str specifications-str]
    (if-let [answers-map (instruments/parse-answers-post instrument-id items-str specifications-str)]
      (do
        (instruments/save-test-answers! instrument-id answers-map)
        (response/found (str "/instrument/summary/" instrument-id)))
      ;; TODO: Add input spec and return proper error
      ("Error")))

(defn summary-page [instrument-id]
  (if-let [answers (instruments/get-instrument-test-answers instrument-id)]
    (bass4.layout/render
      "instrument-answers.html"
      {:items (:items answers) :specifications (:specifications answers) :sums (:sums answers)})))
