(ns bass4.responses.instrument
  (:require [bass4.services.instrument :as instruments]
            [ring.util.http-response :as response]
            [clojure.data.json :as json]))

;; TODO: Add input spec
(defn instrument-page [instrument-id]
  (bass4.layout/render "instrument.html" {:instrument (instruments/get-instrument instrument-id) :instrument-id instrument-id}))

;; TODO: Add input spec
;; TODO: Handle exception if answers cannot be parsed as JSON
(defn post-answers [instrument-id answers]
  (let [a (json/read-str answers)]
    (bass4.layout/render "instrument-answers.html" {:answers a :info (json/read-str "dfjksn4l?++:\"sdfsdf")})))