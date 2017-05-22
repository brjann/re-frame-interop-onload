(ns bass4.responses.assessments
  (:require [bass4.services.assessments :as assessments-service]
            [ring.util.http-response :as response]
            [bass4.utils :refer [json-safe]]
            [clojure.tools.logging :as log]))

(defn- text-page
  [step]
  (assessments-service/text-shown! step)
  (bass4.layout/render "assessment-text.html"
                       {:texts (try (clojure.edn/read-string (:texts step))
                                    (catch Exception e ""))}))

(defn- assessment-page
  [round]
  (let [step (first round)]
    (if (nil? (:instrument-id step))
      (text-page step)
      "all is well!")))


(defn handle-assessments
  [user-id session]
  (log/debug "handle-assessments")
  (let [round (assessments-service/get-assessment-round user-id)]
    (log/debug (seq round))
    (if-not (seq round)
      (-> (response/found "/user")
          (assoc :session (merge session {:assessments-pending false})))
      (assessment-page round))))

