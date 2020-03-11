(ns bass4.instrument.preview
  (:require [bass4.instrument.services :as instruments]
            [bass4.instrument.flagger :as answers-flagger]
            [ring.util.http-response :as http-response]
            [bass4.utils :refer [map-map str->int]]
            [bass4.layout :as layout]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.instrument.validation :as validation]
            [bass4.middleware.request-logger :as request-logger]
            [bass4.services.bass :as bass-service]
            [bass4.instrument.answers-services :as instrument-answers]
            [bass4.utils :as utils]
            [bass4.db.core :as db]))

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

(defn- apply-project-specs
  [db instrument answers]
  (let [projects-specs (answers-flagger/filter-specs
                         instrument
                         (answers-flagger/flagging-specs db))
        namespace      (answers-flagger/namespace-map answers)]
    (utils/map-map
      (fn [specs]
        (map (fn [spec]
               (answers-flagger/eval-spec spec namespace))
             specs))
      projects-specs)))

(defn- sort-projects
  [[x _] [y _]]
  (cond
    (= :test x)
    -1

    (= :test y)
    1

    :else
    (compare x y)))

(defapi summary-page
  [instrument-id :- api/->int]
  (let [instrument    (instruments/get-instrument instrument-id)
        item-answers  (instrument-answers/merge-items-answers
                        instrument
                        (instruments/get-instrument-test-answers instrument-id))
        project-names (assoc
                        (bass-service/project-names db/*db*)
                        :test
                        "Test conditions")
        flag-specs    (->> (apply-project-specs db/*db* instrument item-answers)
                           (sort sort-projects))]
    (when (:items item-answers)
      (bass4.layout/render
        "instrument-answers.html"
        {:items         (:items item-answers)
         :sums          (:sums item-answers)
         :flag-specs    flag-specs
         :project-names project-names}))))