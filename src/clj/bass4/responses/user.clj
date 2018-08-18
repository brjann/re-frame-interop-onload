(ns bass4.responses.user
  (:require [bass4.utils :refer [json-safe]]
            [clojure.tools.logging :as log]
            [bass4.services.privacy :as privacy-service]
            [ring.util.http-response :as http-response]
            [bass4.api-coercion :as api :refer [def-api]]
            [bass4.layout :as layout]
            [bass4.services.user :as user-service]
            [clj-time.core :as t]
            [bass4.services.assessments :as administrations]
            [bass4.services.treatment :as treatment-service]))

(defn user-page-map
  [treatment path]
  (merge (:user-components treatment)
         {:path          path
          :new-messages? (:new-messages? treatment)}))


(defn treatment-mw
  [handler]
  (fn [request]
    (if-let [treatment (when-let [user (get-in request [:db :user])]
                         (treatment-service/user-treatment (:user-id user)))]
      (handler (-> request
                   (assoc-in [:db :treatment] treatment)
                   (assoc-in [:db :render-map] (user-page-map treatment (:uri request)))))
      (handler request))))


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
    (let [session (:session request)]
      (if (:user-id request)
        (if-not (:assessments-checked? session)
          (if (assessments-pending? request)
            (do
              #_(log/debug "Assessments pending!")
              (-> (http-response/found "/assessments")
                  (assoc :session
                         (merge
                           session
                           {:assessments-checked?   true
                            :assessments-pending?   true
                            :assessments-performed? true}))))
            (do
              #_(log/debug "No assessments pending!")
              (-> (http-response/found "/user")
                  (assoc :session
                         (merge
                           session
                           {:assessments-checked? true
                            :assessments-pending? false})))))
          (handler request))
        (handler request)))))


(defn- consent-redirect?
  [request]
  (let [user (get-in request [:db :user])]
    (cond
      (= "/user/privacy-consent" (:uri request))
      false

      (not= :get (:request-method request))
      false

      (:privacy-notice-consent-time user)
      false

      :else
      (privacy-service/user-must-consent? (:project-id user)))))

(defn privacy-consent-mw
  [handler request]
  (if (consent-redirect? request)
    (do
      (log/debug "redirecting")
      (http-response/found "/user/privacy-consent"))
    (handler request)))


(def-api privacy-consent-page
  [user :- map?]
  (let [project-id     (:project-id user)
        privacy-notice (privacy-service/get-privacy-notice project-id)]
    (layout/render "privacy-consent.html"
                   {:privacy-notice privacy-notice})))

(def-api handle-privacy-consent
  [user :- map? i-consent :- api/str+!]
  (let [project-id     (:project-id user)
        privacy-notice (privacy-service/get-privacy-notice project-id)]
    (if-not (and privacy-notice (= "i-consent" i-consent))
      (layout/error-400-page)
      (do
        (user-service/set-user-privacy-consent!
          (:user-id user)
          privacy-notice
          (t/now))
        (http-response/found "/user")))))