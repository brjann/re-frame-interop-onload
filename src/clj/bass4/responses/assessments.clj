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
      (bass4.layout/render "instrument.html"
                           {:instrument instrument
                            :instrument-id instrument-id
                            :order (:instrument-order step)
                            :count (:instrument-count step)})
      (do
        ;; Could not find instrument - return error screen and mark step as completed
        (assessments-service/step-completed! step)
        ;; TODO: This error page does not work - see documentation for for error-page
        (bass4.layout/error-page (str "Instrument " instrument-id " not found"))))))

(defn- assessment-page
  [round]
  (let [step (first round)]
    ;; This makes sure that the thank-you text is shown.
    ;; Bad separation of concern but difficult to place elsewhere
    (assessments-service/batch-must-show-texts! step)
    (if (nil? (:instrument-id step))
      (text-page step)
      (instrument-page step))))

(defn- assessments-completed
  [session]
  (-> (response/found "/user")
      (assoc :session (merge session {:assessments-pending false}))))

(defn- instrument-completed
  [user-id round instrument-id items-str specifications-str]
  (let [administration-ids (map :administration-id (filter #(= (:instrument-id %) instrument-id) round))
        answers-map (instruments/parse-answers-post instrument-id items-str specifications-str)]
    (if-not (or (empty? administration-ids) (nil? answers-map))
      (do (assessments-service/instrument-completed! user-id administration-ids instrument-id answers-map)
          (-> (response/found "/user")
              #_(assoc :session (merge session {:assessments-pending false}))))
      (bass4.layout/error-400-page))))

(defn handle-assessments
  [user-id session]
  (let [round (assessments-service/get-assessment-round user-id)]
    (if-not (seq round)
      (assessments-completed session)
      (assessment-page round))))

;; TODO: Add input validation
(defn post-instrument-answers
  [user-id session instrument-id items specifications]
  (let [round (assessments-service/get-assessment-round user-id)]
    (if-not (seq round)
      (assessments-completed session)
      ;; TODO: Validate input
      (instrument-completed user-id round instrument-id items specifications))))