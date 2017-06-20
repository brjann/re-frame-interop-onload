(ns bass4.responses.instrument
  (:require [bass4.services.instrument :as instruments]
            [ring.util.http-response :as response]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [bass4.layout :as layout]))

(defn instrument-page [instrument-id]
  (bass4.layout/render "instrument.html" {:instrument (instruments/get-instrument instrument-id) :instrument-id instrument-id}))

;; TODO: Add input spec and return proper error
(s/defn ^:always-validate post-answers [instrument-id :- s/Int items-str :- s/Str specifications-str :- s/Str]
    (if-let [answers-map (instruments/parse-answers-post instrument-id items-str specifications-str)]
      (do
        (instruments/save-test-answers! instrument-id answers-map)
        (response/found (str "/instrument/summary/" instrument-id)))
      ;; TODO: Add input spec and return proper error
      (layout/error-400-page)))

(defn summary-page [instrument-id]
  (if-let [answers (instruments/get-instrument-test-answers instrument-id)]
    (bass4.layout/render
      "instrument-answers.html"
      {:items (:items answers) :specifications (:specifications answers) :sums (:sums answers)})))
