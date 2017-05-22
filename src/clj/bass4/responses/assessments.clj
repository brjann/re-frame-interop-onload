(ns bass4.responses.assessments
  (:require [bass4.services.assessments :as assessments-service]
            [ring.util.http-response :as response]
            [bass4.utils :refer [json-safe]]
            [clojure.tools.logging :as log]
            [bass4.services.instrument :as instruments]))

(defn- text-page
  [step]
  (assessments-service/step-completed! step)
  (bass4.layout/render "assessment-text.html"
                       {:texts (try (clojure.edn/read-string (:texts step))
                                    (catch Exception e ""))}))

(defn- instrument-page
  [step]
  (let [instrument-id (:instrument-id step)]
    (if-let [instrument (instruments/get-instrument instrument-id)]
      (bass4.layout/render "instrument.html" {:instrument instrument :instrument-id instrument-id})
      (do
        ;; Could not find instrument - return error screen and mark step as completed
        (assessments-service/step-completed! step)
        (bass4.layout/error-page (str "Instrument " instrument-id " not found"))))))

(defn- assessment-page
  [round]
  (let [step (first round)]
    (if (nil? (:instrument-id step))
      (text-page step)
      (instrument-page step))))

(defn- assessments-completed
  [session]
  (-> (response/found "/user")
      (assoc :session (merge session {:assessments-pending false}))))

(defn- instrument-completed
  [user-id instrument-id session]
  (assessments-service/instrument-completed! user-id instrument-id)
  (-> (response/found "/user")
      #_(assoc :session (merge session {:assessments-pending false}))))

(defn handle-assessments
  [user-id session]
  (let [round (assessments-service/get-assessment-round user-id)]
    (if-not (seq round)
      (assessments-completed session)
      (assessment-page round))))

;; TODO: Add input validation
(defn instrument-answers
  [user-id session instrument-id items specifications]
  (let [round (assessments-service/get-assessment-round user-id)]
    (if-not (seq round)
      (assessments-completed session)
      (instrument-completed user-id instrument-id session))))