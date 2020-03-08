(ns bass4.instrument.preview
  (:require [bass4.instrument.services :as instruments]
            [bass4.instrument.flagger :as answers-flagger]
            [ring.util.http-response :as http-response]
            [bass4.utils :refer [map-map str->int]]
            [bass4.layout :as layout]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.instrument.validation :as validation]
            [bass4.middleware.request-logger :as request-logger]
            [bass4.db.core :as db]
            [bass4.services.bass :as bass-service]
            [bass4.instrument.answers-services :as instrument-answers]))

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

(defn- apply-project-conditions
  [instrument-id answers]
  (let [instrument         (instruments/get-instrument instrument-id)
        project-conditions (answers-flagger/flagging-specs db/*db*)
        project-names      (bass-service/project-names db/*db*)]
    ))

(defapi summary-page
  [instrument-id :- api/->int]
  (let [answers (instrument-answers/merge-items-answers
                  (instruments/get-instrument instrument-id)
                  (instruments/get-instrument-test-answers instrument-id))]
    (when (:items answers)
      (bass4.layout/render
        "instrument-answers.html"
        {:items (:items answers)
         :sums  (:sums answers)}))))