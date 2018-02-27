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
            [bass4.utils :refer [filter-map fnil+ in? json-safe]]
            [bass4.layout :as layout]
            [bass4.i18n :as i18n]
            [clojure.tools.logging :as log]
            [bass4.sms-sender :as sms]
            [bass4.mailer :as mail]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [bass4.services.bass :as bass]))


;; ---------------
;;     CAPTCHA
;; ---------------

;; TODO: Test this
(defn all-fields?
  [fields field-values]
  (if (seq field-values)
    (= (into #{} fields) (into #{} (keys field-values)))))

(defn duplicate-conflict?
  [field-values reg-params]
  (if-let [duplicates-map (merge
                            (when (not (:allow-duplicate-email? reg-params))
                              (select-keys field-values [:email]))
                            (when (not (:allow-duplicate-sms? reg-params))
                              (select-keys field-values [:sms-number])))]
    (reg-service/duplicate-info? duplicates-map)
    false))

(defn complete-registration
  "This function relies on previous checking of presence of field-values"
  [project-id field-values reg-params]
  (if (duplicate-conflict? field-values reg-params)
    (-> (response/found (str "/registration/" project-id "/duplicate"))
        (assoc :session {}))
    (let [user-id (reg-service/create-user! project-id field-values (:group reg-params))]
      (-> (response/found "/user")
          (assoc :session (res-auth/create-new-session {:user-id user-id} {:double-authed true} true))))))

;; ---------------
;;     CAPTCHA
;; ---------------

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
      (merge
        content
        {:filename filename}))))

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

;; --------------
;;   VALIDATION
;; --------------

(defn email-map
  [email]
  (let [code (auth-service/letters-digits 5)]
    (mail/mail! email (i18n/tr [:registration/validation-code]) (str (i18n/tr [:registration/validation-code]) " " code))
    {:code-email code}))

(defn- sms-map
  [sms-number]
  (let [code (auth-service/letters-digits 5)]
    (sms/send-db-sms! sms-number (str (i18n/tr [:registration/validation-code]) " " code))
    {:code-sms code}))

(defn- prepare-validation
  [project-id field-values]
  (let [codes (merge
                (fnil+ sms-map (:sms-number field-values))
                (fnil+ email-map (:email field-values)))]
    (->
      (response/found (str "/registration/" project-id "/validate"))
      (assoc :session {:reg-field-values field-values
                       :reg-codes        codes
                       :captcha-ok       nil}))))

(defn validation-page
  [project-id session]
  (let [field-values (:reg-field-values session)
        codes        (:reg-codes session)]
    (if (or (contains? codes :code-sms) (contains? codes :code-email))
      (layout/render "registration-validation.html"
                     {:email      (:email field-values)
                      :sms-number (:sms-number field-values)
                      :project-id project-id})
      (layout/error-403-page))))

;; TODO: Test this
(defn handle-validation
  [project-id posted-codes session]
  (let [field-values (:reg-field-values session)
        codes        (:reg-codes session)
        reg-params   (reg-service/registration-params project-id)]
    (cond
      ;; All fields are present in session
      (not (all-fields? (:fields reg-params) field-values))
      (layout/error-400-page)

      ;; If check if email code has been submitted if required
      (when (contains? codes :code-email)
        (not (contains? posted-codes :code-email)))
      (layout/error-400-page)

      ;; If check if sms code has been submitted if required
      (when (contains? codes :code-sms)
        (not (contains? posted-codes :code-sms)))
      (layout/error-400-page)

      ;; Check if email code is correct
      (and (contains? codes :code-email)
           (not= (string/trim (:code-email posted-codes)) (:code-email codes)))
      (layout/error-422 "email-error")

      ;; Check if sms code is correct
      (and (contains? codes :code-sms)
           (not= (string/trim (:code-sms posted-codes)) (:code-sms codes)))
      (layout/error-422 "sms-error")

      :else
      (complete-registration project-id field-values reg-params))))


;; -------------
;;   DUPLICATE
;; -------------

(defn duplicate-page
  [project-id]
  (let [emails (bass/db-contact-info project-id)]
    (layout/render "registration-duplicate.html"
                   {:email      (or (:project-email emails)
                                    (:db-email emails))
                    :project-id project-id})))

;; --------------
;;   REGISTRATION
;; --------------

(defn registration-page
  [project-id]
  (let [params        (reg-service/registration-content project-id)
        fields        (:fields params)
        fields-map    (zipmap fields (repeat (count fields) true))
        sms-countries (str "[\"" (string/join "\",\"" (:sms-countries params)) "\"]")]
    (layout/render "registration-form.html"
                   (merge
                     params
                     fields-map
                     {:sms-countries sms-countries}))))

(def country-codes
  (group-by #(string/lower-case (get % "code")) (json-safe (slurp (io/resource "docs/country-calling-codes.json")))))

(defn- check-sms
  [sms-number sms-countries]
  (cond
    (nil? sms-number)
    true

    (not (string/starts-with? sms-number "+"))
    false

    :else
    (let [matching (-> (select-keys country-codes sms-countries)
                       vals
                       flatten)]
      (some
        #(string/starts-with?
           sms-number
           (str "+" (get % "callingCode")))
        matching))))


(defn handle-registration
  [project-id posted-fields]
  (let [params       (reg-service/registration-params project-id)
        fields       (:fields params)
        field-values (select-keys posted-fields fields)]
    (if (all-fields? fields field-values)
      (if (check-sms (:sms-number field-values) (:sms-countries params))
        (if (or (contains? field-values :sms-number) (contains? field-values :email))
          (prepare-validation project-id field-values)
          (complete-registration project-id field-values params))
        (layout/error-422 "sms-country-error"))
      (layout/error-400-page))))


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
;; TODO: Login only if there are assessments waiting
;; TODO: Max number of sms