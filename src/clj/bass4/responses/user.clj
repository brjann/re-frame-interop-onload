(ns bass4.responses.user
  (:require [bass4.utils :refer [json-safe]]
            [clojure.tools.logging :as log]
            [bass4.services.privacy :as privacy-service]
            [ring.util.http-response :as http-response]
            [markdown.core :as md]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.layout :as layout]
            [bass4.services.user :as user-service]
            [clj-time.core :as t]
            [bass4.services.assessments :as administrations]
            [bass4.services.treatment :as treatment-service]
            [bass4.services.bass :as bass]
            [bass4.i18n :as i18n]))

(defn user-page-map
  [treatment path]
  (merge (:user-components treatment)
         {:path          path
          :new-messages? (:new-messages? treatment)}))


(defn treatment-mw
  [handler]
  (fn [request]
    (log/debug "xxx")
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
      (cond
        (:assessments-checked? session)
        (handler request)

        (assessments-pending? request)
        (do
          #_(log/debug "Assessments pending!")
          (-> (http-response/found "/user/assessments")
              (assoc :session
                     (merge
                       session
                       {:assessments-checked?   true
                        :assessments-pending?   true
                        :assessments-performed? true}))))

        :else
        (let [out-response (handler request)
              out-session  (or (:session out-response)
                               session)]
          (assoc out-response
            :session
            (merge
              out-session
              {:assessments-checked? true
               :assessments-pending? false})))))))

(defapi privacy-notice-bare
  [user :- map?]
  (let [project-id  (:project-id user)
        notice-text (:notice-text (privacy-service/get-privacy-notice project-id))]
    (when (nil? notice-text)
      (throw (Exception. "Missing privacy notice when showing bare. Guards have failed.")))
    (layout/text-response (md/md-to-html-string notice-text))))

(defapi privacy-consent-page
  [user :- map?]
  (let [project-id  (:project-id user)
        notice-text (:notice-text (privacy-service/get-privacy-notice project-id))
        email       (:email (bass/db-contact-info project-id))]
    (when (nil? notice-text)
      (throw (Exception. "Missing privacy notice when requesting consent. Guards have failed.")))
    (layout/render "privacy-consent.html"
                   {:privacy-notice notice-text
                    :email          email})))

(defapi privacy-notice-page
  [user :- map? render-map :- map?]
  (let [project-id  (:project-id user)
        notice-text (:notice-text (privacy-service/get-privacy-notice project-id))]
    (when (nil? notice-text)
      (throw (Exception. "Missing privacy notice when showing. Guards have failed.")))
    (layout/render "privacy-notice.html"
                   (merge render-map
                          {:user           user
                           :page-title     (i18n/tr [:privacy-notice/notice-title])
                           :privacy-notice notice-text}))))

(defn consent-response
  [user notice-id]
  (user-service/set-user-privacy-consent!
    (:user-id user)
    notice-id
    (t/now))
  (user-service/close-no-consent-flag! (:user-id user) (t/now))
  (http-response/found "/user"))

(defn no-consent-response
  [user]
  (user-service/create-no-consent-flag! (:user-id user))
  (http-response/found "/logout"))

(defapi handle-privacy-consent
  [user :- map? i-consent :- [[api/str? 1 20]]]
  (let [project-id (:project-id user)
        notice-id  (:notice-id (privacy-service/get-privacy-notice project-id))]
    (cond
      (not notice-id)
      (throw (Error. (str "No ID for privacy notice in project" project-id ". Guards missing")))

      (= "i-consent" i-consent)
      (consent-response user notice-id)

      (= "no-consent" i-consent)
      (no-consent-response user)

      :else
      (layout/error-400-page))))