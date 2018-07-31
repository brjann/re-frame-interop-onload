(ns bass4.responses.assessments
  (:require [bass4.services.assessments :as assessments-service]
            [ring.util.http-response :as http-response]
            [bass4.utils :refer [json-safe]]
            [clojure.tools.logging :as log]
            [bass4.services.instrument :as instruments]
            [bass4.request-state :as request-state]
            [schema.core :as s]))

(defn- text-page
  [step]
  (request-state/add-to-state-key! :info "Assessment text")
  (assessments-service/step-completed! step)
  (bass4.layout/render "assessment-text.html"
                       {:texts (try (clojure.edn/read-string (:texts step))
                                    (catch Exception e ""))}))

(defn- instrument-page
  [step]
  (let [instrument-id (:instrument-id step)]
    (request-state/add-to-state-key! :info (str "Instrument " instrument-id))
    (if-let [instrument (instruments/get-instrument instrument-id)]
      (bass4.layout/render "assessment-instrument.html"
                           {:instrument instrument
                            :instrument-id instrument-id
                            :order (:instrument-order step)
                            :count (:instrument-count step)})
      (do
        ;; Could not find instrument - return error screen and mark step as completed
        (assessments-service/step-completed! step)
        (request-state/record-error! (str "Instrument " instrument-id " not found when doing assessment"))
        (http-response/found "/user")))))

(defn- assessment-page
  [round]
  (let [step (first round)]
    ;; This makes sure that the thank-you text is shown.
    ;; Bad separation of concern but difficult to place elsewhere
    (assessments-service/batch-must-show-texts! step)
    (if (nil? (:instrument-id step))
      ;; TODO: The pre-checking of whether the assessment is completed should be done here.
      (text-page step)
      (instrument-page step))))

(defn- assessments-completed
  [session]
  (-> (http-response/found "/user")
      (assoc :session (merge session {:assessments-pending? false}))))

(defn- instrument-completed
  [user-id round instrument-id items-str specifications-str]
  (let [administration-ids (map :administration-id (filter #(= (:instrument-id %) instrument-id) round))
        answers-map (instruments/parse-answers-post instrument-id items-str specifications-str)]
    (if-not (or (empty? administration-ids) (nil? answers-map))
      (do (assessments-service/instrument-completed! user-id administration-ids instrument-id answers-map)
          (assessments-service/administrations-completed! user-id round instrument-id)
          (-> (http-response/found "/user")))
      (do
        (request-state/record-error! "Something went wrong")
        (http-response/found "/user")))))

(defn handle-assessments
  [user-id session]
  (let [round (assessments-service/get-assessment-round user-id)]
    (if-not (seq round)
      (assessments-completed session)
      (assessment-page round))))

(s/defn ^:always-validate post-instrument-answers
  [user-id session instrument-id :- s/Int items :- s/Str specifications :- s/Str]
  (let [round (assessments-service/get-assessment-round user-id)]
    (if-not (seq round)
      (assessments-completed session)
      (instrument-completed user-id round instrument-id items specifications))))