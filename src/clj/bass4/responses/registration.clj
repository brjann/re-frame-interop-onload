(ns bass4.responses.registration
  (:require [ring.util.http-response :as response]
            [schema.core :as s]
            [bass4.config :refer [env]]
            [bass4.captcha :as captcha]
            [clojure.walk :as walk]
            [bass4.services.registration :as reg-service]
            [bass4.responses.auth :as res-auth]
            [bass4.services.auth :as auth-service]
            [clj-time.core :as t]
            [bass4.utils :refer [filter-map fnil+ in?]]
            [bass4.layout :as layout]
            [bass4.i18n :as i18n]
            [clojure.tools.logging :as log]
            [bass4.sms-sender :as sms]
            [bass4.mailer :as mail]
            [clojure.string :as string]))

(defn- captcha-session
  [project-id]
  (let [{:keys [filename digits]} (captcha/captcha!)]
    (-> (response/found (str "/registration/" project-id "/captcha"))
        (assoc :session {:captcha-filename filename :captcha-digits digits}))))

(defn- captcha-page
  [project-id filename]
  (let [content (reg-service/captcha-content project-id)]
    (layout/render
      "registration-captcha.html"
      {:content  content
       :filename filename})))

(defn captcha
  [project-id session]
  (if (:captcha-ok session)
    (response/found (str "/registration/" project-id))
    (let [filename (:captcha-filename session)
          digits   (:captcha-digits session)]
      (if (and filename digits)
        (captcha-page project-id filename)
        (captcha-session project-id)))))

(defn validate-captcha
  [project-id captcha session]
  (if-let [digits (:captcha-digits session)]
    (if (= digits captcha)
      (-> (response/found (str "/registration/" project-id ""))
          (assoc :session {:captcha-ok true}))
      (layout/error-422 "error"))
    (response/found (str "/registration/" project-id "/captcha"))))

(defn registration
  [project-id]
  (let [params        (reg-service/registration-params project-id)
        fields        (:fields params)
        fields-map    (zipmap fields (repeat (count fields) true))
        sms-countries (str "[\"" (string/join "\",\"" (:sms-countries params)) "\"]")]
    (layout/render "registration-form.html"
                   (merge
                     params
                     fields-map
                     {:sms-countries sms-countries}))))

(defn- map-fields
  [fields-mapping fields]
  (->> fields-mapping
       (map #(vector (first %) (get fields (second %))))
       (into {})
       (filter-map identity)
       (walk/keywordize-keys)))

(defn email-map
  [email]
  (let [code (auth-service/letters-digits 5)]
    (mail/mail! email (i18n/tr [:registration/validation-code]) (str (i18n/tr [:registration/validation-code]) " " code))
    {:code-Email code}))

(defn- sms-map
  [sms-number]
  (let [code (auth-service/letters-digits 5)]
    (sms/send-db-sms! sms-number (str (i18n/tr [:registration/validation-code]) " " code))
    {:code-SMS code}))

(defn- prepare-validation
  [project-id field-values]
  (let [info (merge
               field-values
               (fnil+ sms-map (:SMSNumber field-values))
               (fnil+ email-map (:Email field-values)))]
    (->
      (response/found (str "registration/" project-id "/validate"))
      (assoc :session {:reg-info   info
                       :captcha-ok nil}))))

(defn handle-registration
  [project-id fields]
  (let [{:keys [fields-mapping group]} (reg-service/registration-params project-id)
        field-values (map-fields fields-mapping fields)]
    (if (seq field-values)
      (prepare-validation project-id field-values)
      (layout/error-400-page))))

(defn validate-registration
  [project-id session]
  (let [info (:reg-info session)]))

;; TODO: Tests for registration and following assessments
;; TODO: Duplicate username
;; TODO: Validate email and sms
;; Idea: If email and/or sms is entered, then all field values
;; are stored in session and validation codes are sent out.
;; Registrant must enter these before user is created.
;; Auto-create password and present username/password to user if no sms/email,
;; otherwise send them to user by sms first and by email second
;;
;; TODO: Captcha timeout
;; TODO: Registration closed screen
;; TODO: Max number of sms