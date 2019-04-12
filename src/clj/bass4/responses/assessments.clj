(ns bass4.responses.assessments
  (:require [bass4.services.assessments :as assessments-service]
            [ring.util.http-response :as http-response]
            [bass4.utils :refer [json-safe]]
            [clojure.tools.logging :as log]
            [bass4.instrument.services :as instruments]
            [bass4.request-state :as request-state]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.instrument.validation :as validation]
            [bass4.services.assessments :as administrations]
            [bass4.session.utils :as session-utils]))


;; --------------------------
;;         MIDDLEWARE
;; --------------------------

(defn- assessments-pending?
  [request]
  (let [user-id (:user-id request)]
    (cond
      (nil? user-id)
      false

      :else
      (< 0 (administrations/create-assessment-round-entries! user-id)))))

(defn check-assessments-mw
  [handler]
  (fn [request]
    (let [session-in (:session request)]
      (cond
        (:assessments-checked? session-in)
        (handler request)

        (assessments-pending? request)
        (do
          #_(log/debug "Assessments pending!")
          (-> (http-response/found "/user/assessments")
              (assoc :session
                     (merge
                       session-in
                       {:assessments-checked?   true
                        :assessments-pending?   true
                        :assessments-performed? true}))))

        :else
        #_(let [out-response (handler request)
                out-session  (or (:session out-response)
                                 session-in)]
            (assoc out-response
              :session
              (merge
                out-session
                {:assessments-checked? true
                 :assessments-pending? false})))
        (session-utils/assoc-out-session (handler request)
                                         session-in
                                         {:assessments-checked?  true
                                           :assessments-pending? false})))))

;; --------------------------
;;         RESPONSES
;; --------------------------

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
                           {:instrument    instrument
                            :instrument-id instrument-id
                            :order         (:instrument-order step)
                            :count         (:instrument-count step)})
      (do
        ;; Could not find instrument - record error and mark step as completed
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
  [user-id round instrument-id items specifications]
  (if-let [administration-ids (map :administration-id (filter #(= (:instrument-id %) instrument-id) round))]
    (if-let [instrument (instruments/get-instrument instrument-id)]
      (do
        (validation/validate-answers instrument items specifications)
        (let [answers-map (instruments/score-instrument instrument-id items specifications)]
          (assessments-service/instrument-completed! user-id administration-ids instrument-id answers-map)
          (assessments-service/check-completed-administrations! user-id round instrument-id)
          (-> (http-response/found "/user/assessments"))))
      (do
        (request-state/record-error! (str "Instrument " instrument-id " does not exist."))
        (http-response/found "/user")))
    (do
      (request-state/record-error! (str "Instrument " instrument-id " not in ongoing assessments."))
      (http-response/found "/user"))))


;; --------------------------
;;            API
;; --------------------------


(defapi handle-assessments
  [user-id :- integer? session :- [:? map?]]
  (let [round (assessments-service/get-assessment-round user-id)]
    (if-not (seq round)
      (assessments-completed session)
      (assessment-page round))))

(defapi post-instrument-answers
  [user-id :- integer? session :- [:? map?] instrument-id :- api/->int items :- [api/->json map?] specifications :- [api/->json map?]]
  (let [round (assessments-service/get-assessment-round user-id)]
    (if-not (seq round)
      (assessments-completed session)
      (instrument-completed user-id round instrument-id items specifications))))