(ns bass4.responses.privacy
  (:require [markdown.core :as md]
            [clj-time.core :as t]
            [ring.util.http-response :as http-response]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.services.privacy :as privacy-service]
            [bass4.layout :as layout]
            [bass4.services.bass :as bass]
            [bass4.i18n :as i18n]
            [bass4.services.user :as user-service]))

(defn privacy-notice
  [user]
  (let [project-id  (:project-id user)
        notice-text (:notice-text (privacy-service/get-privacy-notice project-id))]
    (when (nil? notice-text)
      (throw (Exception. "Missing privacy notice when showing bare. Guards have failed.")))
    notice-text))

(defapi privacy-notice-html
  [user :- map?]
  (let [notice-text (privacy-notice user)]
    (layout/text-response (md/md-to-html-string notice-text))))

(defapi privacy-notice-raw
  [user :- map?]
  (layout/text-response (privacy-notice user)))

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
      (http-response/bad-request))))