(ns bass4.responses.registration
  (:require [ring.util.http-response :as response]
            [schema.core :as s]
            [bass4.config :refer [env]]
            [bass4.captcha :as captcha]
            [bass4.passwords :as passwords]
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
            [bass4.services.bass :as bass]
            [bass4.services.assessments :as assessments]
            [bass4.http-utils :as h-utils]
            [bass4.responses.e-auth :as e-auth]
            [clojure.set :as set])
  (:import (java.util UUID)))


(defn- all-fields?
  [fields field-values]
  (if (seq field-values)
    (= (into #{} fields) (into #{} (keys field-values)))))

;; ------------
;; FINISHED
;; ------------

(defn assoc-reg-session
  [response session reg-map]
  (let [reg-session (:registration session)]
    (assoc-in response [:session :registration] (merge reg-session reg-map))))

(defn reset-reg-session
  [response session]
  (assoc-in response [:session :registration] nil))

(defn- to-finished-page
  [project-id session]
  (->
    (response/found (str "/registration/" project-id "/finished"))
    (reset-reg-session session)))

(defn- to-assessments
  [project-id user-id request]
  (let [return-url (when (reg-service/show-finished-screen? project-id)
                     (str (h-utils/get-host-address request) "/registration/" project-id "/finished"))]
    (->
      (response/found "/user")
      (assoc :session (res-auth/create-new-session
                        {:user-id user-id}
                        {:external-login true :return-url return-url}
                        true)))))

(defn finished-router
  [project-id session request]
  (if-let [user-id (get-in session [:registration :credentials :user-id])]
    (if (zero? (count (assessments/get-pending-assessments user-id)))
      (to-finished-page project-id session)
      (to-assessments project-id user-id request))
    (layout/render "registration-finished.html"
                   (reg-service/finished-content project-id))))

;; ------------
;; CREDENTIALS
;; ------------

#_(defn- login-url
    [request]
    (let [host   (h-utils/get-host request)
          scheme (name (:scheme request))]
      (str scheme "://" host)))

(defn credentials-page
  [project-id session request]
  (let [credentials (get-in session [:registration :credentials])]
    (if (contains? credentials :username)
      (layout/render "registration-credentials.html"
                     {:username   (:username credentials)
                      :password   (:password credentials)
                      :login-url  (h-utils/get-host-address request)
                      :project-id project-id})
      ;; Wrong place - redirect
      (response/found (str "/registration/" project-id)))))

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


(defn- duplicate-conflict?
  [field-values reg-params]
  (if-let [duplicates-map (merge
                            (when (not (:allow-duplicate-email? reg-params))
                              (select-keys field-values [:email]))
                            (when (not (:allow-duplicate-sms? reg-params))
                              (select-keys field-values [:sms-number])))]
    (reg-service/duplicate-info? duplicates-map)
    false))


;; ---------------
;;  USER CREATION
;; ---------------

(defn- gen-username
  [field-values participant-id reg-params]
  (let [username (case (:auto-username reg-params)
                   :email (:email field-values)
                   :participant-id participant-id
                   :none ""
                   nil)]
    (if (nil? username)
      (throw (Exception. (str "No value for auto username " (:auto-username reg-params))))
      (when (not= "" username)
        username))))

(defn- gen-participant-id
  [project-id reg-params]
  (when (:auto-id? reg-params)
    (reg-service/generate-participant-id
      project-id
      (:auto-id-prefix reg-params)
      (:auto-id-length reg-params))))

(defn- gen-password
  [field-values reg-params]
  (if (:auto-password? reg-params)
    (assoc field-values :password (passwords/password))
    field-values))

(defn- created-redirect
  [project-id user-id username password auto-password? session]
  (if username
    (->
      (response/found (str "/registration/" project-id "/credentials"))
      (assoc-reg-session session {:credentials (merge
                                                 {:user-id user-id :username username}
                                                 (if auto-password? {:password password}))}))
    (->
      (response/found (str "/registration/" project-id "/finished"))
      (assoc-reg-session session {:credentials {:user-id user-id}}))))

(defn- create-user
  [project-id field-values reg-params session]
  (let [participant-id (gen-participant-id project-id reg-params)
        username       (gen-username field-values participant-id reg-params)
        field-values   (gen-password field-values reg-params)
        user-id        (reg-service/create-user! project-id field-values username participant-id (:group reg-params))]
    (-> (created-redirect project-id user-id username (:password field-values) (:auto-password? reg-params) session))))

(defn- complete-registration
  "This function relies on previous checking of presence of field-values"
  [project-id field-values reg-params session]
  (if (duplicate-conflict? field-values reg-params)
    (-> (response/found (str "/registration/" project-id "/duplicate"))
        (reset-reg-session session))
    (create-user project-id field-values reg-params session)))

;; ---------------
;;     CAPTCHA
;; ---------------

(defn- captcha-session
  [project-id session]
  (let [{:keys [filename digits]} (captcha/captcha!)]
    (-> (response/found (str "/registration/" project-id "/captcha"))
        (assoc-reg-session session {:captcha-filename  filename
                                    :captcha-digits    digits
                                    :captcha-timestamp (t/now)
                                    :captcha-tries     0}))))

(defn- captcha-page
  [project-id filename]
  (let [content (reg-service/captcha-content project-id)]
    (layout/render
      "registration-captcha.html"
      (merge
        content
        {:project-id project-id
         :filename   filename}))))

(def ^:const const-captcha-tries 5)
(def ^:const const-captcha-timeout 60)

(defn captcha-valid?
  "60 seconds before new captcha is generated
   5 tries before new captcha is generated"
  [session]
  (let [reg-session (:registration session)
        timestamp   (:captcha-timestamp reg-session)
        tries       (:captcha-tries reg-session)]
    (when (and timestamp tries)
      (let [time-elapsed (t/in-seconds (t/interval timestamp (t/now)))]
        (and (> const-captcha-timeout time-elapsed)
             (> const-captcha-tries tries))))))

(defn current-captcha
  [session]
  (let [reg-session (:registration session)
        filename    (:captcha-filename reg-session)
        digits      (:captcha-digits reg-session)]
    (when (and filename digits (captcha-valid? session))
      {:filename filename
       :digits   digits})))

(defn captcha
  [project-id session]
  (if (get-in session [:registration :captcha-ok?])
    (response/found (str "/registration/" project-id))
    (let [{:keys [filename digits]} (current-captcha session)]
      (if (and filename digits)
        (captcha-page project-id filename)
        (captcha-session project-id session)))))

(defn- inc-tries
  [session]
  (let [tries (inc (get-in session [:registration :captcha-tries]))]
    (assoc-in session [:registration :captcha-tries] tries)))

(defn- captcha-digits
  [session]
  (when (captcha-valid? session)
    (get-in session [:registration :captcha-digits])))

(defn- wrong-captcha-response
  [project-id new-session]
  (if (captcha-valid? new-session)
    (-> (layout/error-422 "error")
        (assoc :session new-session))
    (-> (response/found (str "/registration/" project-id "/captcha"))
        (assoc :session new-session))))

(defn validate-captcha
  [project-id captcha session]
  (if-let [digits (captcha-digits session)]
    (if (= digits captcha)
      (let [params (reg-service/registration-params project-id)]
        (if (:bankid? params)
          (-> (response/found (str "/registration/" project-id "/bankid"))
              (assoc-reg-session session {:captcha-ok? true}))
          (-> (response/found (str "/registration/" project-id ""))
              (assoc-reg-session session {:captcha-ok? true}))))
      (wrong-captcha-response project-id (inc-tries session)))
    (response/found (str "/registration/" project-id "/captcha"))))

;; --------------
;;   VALIDATION
;; --------------

(def validation-code-length 5)

(defn email-map
  [email]
  (let [code (auth-service/letters-digits validation-code-length)]
    (mail/mail! email (i18n/tr [:registration/validation-code]) (str (i18n/tr [:registration/validation-code]) " " code))
    {:code-email code}))

(defn- sms-map
  [sms-number]
  (let [code (auth-service/letters-digits validation-code-length)]
    (sms/send-db-sms! sms-number (str (i18n/tr [:registration/validation-code]) " " code))
    {:code-sms code}))

(defn- prepare-validation
  [project-id field-values session]
  (let [codes (merge
                (fnil+ sms-map (:sms-number field-values))
                (fnil+ email-map (:email field-values))
                {:uid (UUID/randomUUID)})]
    (->
      (response/found (str "/registration/" project-id "/validate"))
      (assoc-reg-session session {:field-values     field-values
                                  :validation-codes codes
                                  :captcha-ok?      nil}))))

(defn validation-page
  [project-id session]
  (let [reg-session  (:registration session)
        field-values (:field-values reg-session)
        codes        (:validation-codes reg-session)]
    (if (or (contains? codes :code-sms) (contains? codes :code-email))
      (layout/render "registration-validation.html"
                     (merge
                       {:email      (:email field-values)
                        :sms-number (:sms-number field-values)
                        :project-id project-id}
                       (when (or (env :dev) (env :debug-mode))
                         codes)))
      ;; Wrong page - redirect
      (response/found (str "/registration/" project-id)))))

#_(defn handle-validation
    [project-id posted-codes session]
    (let [reg-session  (:registration session)
          field-values (:field-values reg-session)
          codes        (:validation-codes reg-session)
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
        (complete-registration project-id field-values reg-params session))))

(def validated-codes (atom {}))

;; TODO: Evict old code uids
(defn- code-validated!
  [uid code-key]
  (swap! validated-codes #(assoc % uid (set/union (get % uid) #{code-key}))))

(defn- code-validated?
  [uid code-key]
  (contains? (get @validated-codes uid) code-key))

(defn- code-correct?!
  [uid code-key validation-codes posted-code]
  (if (code-validated? uid code-key)
    true
    (when (= (string/trim posted-code) (get validation-codes code-key))
      (code-validated! uid code-key)
      true)))

(defn- all-codes-validated?
  [uid validation-codes]
  (= (get @validated-codes uid) (set/difference (set (keys validation-codes)) #{:uid})))

(defn validate-code
  [project-id code-key posted-code session]
  (let [reg-session      (:registration session)
        field-values     (:field-values reg-session)
        validation-codes (:validation-codes reg-session)
        uid              (:uid validation-codes)
        reg-params       (reg-service/registration-params project-id)]
    (if (not (all-fields? (:fields reg-params) field-values))
      (layout/error-400-page)
      (let [correct? (code-correct?! uid code-key validation-codes posted-code)]
        (if (all-codes-validated? uid validation-codes)
          (complete-registration project-id field-values reg-params session)
          (if correct?
            (response/ok)
            (layout/error-422 "error")))))))


;; --------------
;;     BANKID
;; --------------
(defn bankid-done?
  [session]
  (let [e-auth (:e-auth session)]
    (and e-auth (every? e-auth [:personnummer :first-name :last-name]))))

(defn bankid-page
  [project-id]
  (let [params (reg-service/registration-content project-id)]
    (layout/render
      "registration-bankid.html"
      (merge
        params
        {:project-id project-id}))))

(defn bankid-finished
  "DOES NOT Reset captcha but continues registration"
  [project-id session]
  (if (bankid-done? session)
    (->
      (response/found (str "/registration/" project-id))
      #_(assoc-reg-session session {:captcha-ok? nil}))
    (throw (ex-info "BankID returned incomplete complete info" (:e-auth session)))))

(defn bankid-poster
  [project-id personnummer session]
  (if (e-auth/personnummer-valid? personnummer)
    (e-auth/launch-bankid session personnummer (str "/registration/" project-id "/bankid-finished") (str "/registration/" project-id "/bankid"))
    (layout/error-400-page (str "Personnummer does not have valid format " personnummer))))

;; --------------
;;   REGISTRATION
;; --------------


(defn bankid-params
  [session params]
  (let [e-auth (:e-auth session)]
    (merge
      {:first-name-value   (:first-name e-auth)
       :last-name-value    (:last-name e-auth)
       :personnummer-value (:personnummer e-auth)}
      (if (:bankid-change-names? params)
        {:first-name true
         :last-name  true}
        {:first-name false
         :last-name  false}))))

(defn registration-page
  [project-id session]
  (let [params        (reg-service/registration-content project-id)
        fields        (:fields params)
        fields-map    (zipmap fields (repeat (count fields) true))
        sms-countries (str "[\"" (string/join "\",\"" (:sms-countries params)) "\"]")]
    ;; BankID already checked by captcha middleware
    (let [bankid-info (when (:bankid? params)
                        (bankid-params session params))]
      (response/found (str "/registration/" project-id "/bankid"))
      (layout/render "registration-form.html"
                     (merge
                       params
                       fields-map
                       bankid-info
                       {:sms-countries sms-countries})))))

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

(defn get-bankid-fields
  [posted-fields session params]
  (when (:bankid? params)
    (let [e-auth (:e-auth session)]
      (if (:bankid-change-names? params)
        {:pid-number (:personnummer e-auth)
         :first-name (:first-name posted-fields)
         :last-name  (:last-name posted-fields)}
        {:pid-number (:personnummer e-auth)
         :first-name (:first-name e-auth)
         :last-name  (:last-name e-auth)}))))

(defn handle-registration
  [project-id posted-fields session]
  (let [params       (reg-service/registration-params project-id)
        fields       (:fields params)
        field-values (merge (select-keys posted-fields fields)
                            (get-bankid-fields posted-fields session params))]
    (if (all-fields? fields field-values)
      (if (check-sms (:sms-number field-values) (:sms-countries params))
        (if (or (contains? field-values :sms-number) (contains? field-values :email))
          (prepare-validation project-id field-values session)
          (complete-registration project-id field-values params session))
        (layout/error-422 "sms-country-error"))
      (layout/error-400-page))))


;; --------------
;;     CANCEL
;; --------------

(defn cancel-registration
  [project-id session]
  (-> (response/found (str "/registration/" project-id))
      (reset-reg-session session)))

;; TODO: Max number of sms