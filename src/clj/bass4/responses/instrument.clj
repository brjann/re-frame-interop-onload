(ns bass4.responses.instrument
  (:require [bass4.services.instrument :as instruments]
            [ring.util.http-response :as response]))

;; TODO: Add input spec
(defn instrument-page [instrument-id]
  (bass4.layout/render "instrument.html" {:instrument (instruments/get-instrument instrument-id) :instrument-id instrument-id}))

;; TODO: Add input spec
(defn post-answers [instrument-id answers]
  (bass4.layout/render "instrument-answers.html" {:answers answers :info (class answers)}))