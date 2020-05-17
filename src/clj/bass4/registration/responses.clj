(ns bass4.registration.responses
  (:require [ring.util.http-response :as http-response]
            [clj-time.core :as t]
            [clojure.core.cache :as cache]
            [clojure.tools.logging :as log]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [bass4.config :refer [env]]
            [bass4.captcha :as captcha]
            [bass4.passwords :as passwords]
            [bass4.registration.services :as reg-service]
            [bass4.responses.auth :as res-auth]
            [bass4.utils :refer [filter-map fnil+ in? json-safe]]
            [bass4.layout :as layout]
            [bass4.i18n :as i18n]
            [bass4.external-messages.sms-sender :as sms]
            [bass4.external-messages.email-sender :as mail]
            [bass4.services.bass :as bass]
            [bass4.assessment.ongoing :as assessment-ongoing]
            [bass4.http-utils :as h-utils]
            [bass4.responses.e-auth :as e-auth]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.services.privacy :as privacy-service]
            [bass4.services.user :as user-service]
            [bass4.http-errors :as http-errors]
            [bass4.db.core :as db]
            [bass4.clients.core :as clients]
            [bass4.now :as now])
  (:import (java.util UUID)))

(defn render-page
  [project-id template & [params]]
  (layout/render template
                 (merge params
                        {:logout-path      (str "/registration/" project-id)
                         :logout-path-text (i18n/tr [:registration/register-again])})))

(defn all-fields?
  [fields field-values]
  (if (seq field-values)
    (= (into #{} fields) (into #{} (keys field-values)))))


(defn assoc-reg-session
  [response session reg-map]
  (let [reg-session (merge (:registration session) reg-map)]
    (assoc response :session (assoc session :registration reg-session))))

(defn reset-reg-session
  [response session]
  (assoc response :session (dissoc session :registration)))

(defn create-session
  [project-id user-id redirect return-to-registration?]
  (->
    (http-response/found redirect)
    (assoc :session (res-auth/create-new-session
                      (user-service/get-user user-id)
                      (merge
                        {:external-login? true}
                        (when return-to-registration?
                          {:logout-path          (str "/registration/" project-id)
                           :logout-path-text-key :registration/register-again}))))))


;; -----------------
;;  RESUME FINISHED
;; -----------------

(defapi resuming-assessments-page
  [project-id :- api/->int request]
  (let [user         (get-in request [:db :user])
        credentials? (and (not (empty? (:username user)))
                          (not (empty? (:password user))))]
    (render-page
      project-id
      "registration-resuming-assessments.html"
      {:credentials? credentials?})))

(defapi resuming-finished-page
  [project-id :- api/->int]
  (let [emails (bass/db-contact-info project-id)]
    (render-page project-id
                 "registration-resuming-finished.html"
                 {:email      (:email emails)
                  :project-id project-id})))


(defn- to-resuming-finished
  [project-id session]
  (->
    (http-response/found (str "/registration/" project-id "/resuming-finished"))
    (reset-reg-session session)))

(defn- to-resuming-assessments
  [project-id user-id credentials?]
  (create-session project-id user-id "resuming-assessments" (not credentials?)))

;; ------------
;;   FINISHED
;; ------------

(defn- to-finished
  [project-id session]
  (->
    (http-response/found (str "/registration/" project-id "/finished"))
    (reset-reg-session session)))

(defapi no-credentials-resume-info-page
  [project-id]
  (render-page project-id
               "registration-no-credentials-resume-info.html"
               (reg-service/finished-content project-id)))

(defn- to-no-credentials-resume-info
  [project-id user-id]
  (create-session project-id user-id "no-credentials-resume-info" true))

(defn- to-assessments
  [project-id user-id credentials?]
  (create-session project-id user-id "/user" (not credentials?)))

(defapi finished-router
  [project-id :- api/->int session :- [:? map?] reg-params]
  (let [reg-session (:registration session)]
    (if-let [user-id (get-in reg-session [:credentials :user-id])]
      (let [ongoing-assessments? (pos? (count (assessment-ongoing/ongoing-assessments user-id)))
            credentials?         (contains? (:credentials reg-session) :username)]
        (cond
          (and (:resume? reg-session) ongoing-assessments?)
          (to-resuming-assessments project-id user-id credentials?)

          (:resume? reg-session)
          (to-resuming-finished project-id session)

          (and ongoing-assessments? (or credentials? (not (:allow-resume? reg-params))))
          (to-assessments project-id user-id credentials?)

          ongoing-assessments?
          (to-no-credentials-resume-info project-id user-id)

          :else
          (to-finished project-id session)))
      (render-page project-id
                   "registration-finished.html"
                   (reg-service/finished-content project-id)))))

;; ------------
;; CREDENTIALS
;; ------------


(defapi credentials-page
  [project-id :- api/->int session :- [:? map?] request]
  (let [credentials (get-in session [:registration :credentials])]
    (if (contains? credentials :username)
      (do
        (when-not (:user-id credentials)
          (throw (ex-info "Credentials did not include user-id" credentials)))
        (render-page project-id
                     "registration-credentials.html"
                     {:username     (:username credentials)
                      :password     (:password credentials)
                      :login-url    (h-utils/get-host-address request)
                      :project-id   project-id
                      :assessments? (assessment-ongoing/ongoing-assessments (:user-id credentials))}))
      ;; Wrong place - redirect
      (http-response/found (str "/registration/" project-id)))))

;; -------------
;;   DUPLICATE
;; -------------


(defapi duplicate-page
  [project-id :- api/->int]
  (let [emails (bass/db-contact-info project-id)]
    (render-page project-id
                 "registration-duplicate.html"
                 {:email      (:email emails)
                  :project-id project-id})))

(defn- resolve-duplicate
  [existing-user reg-fields reg-params]
  (cond
    (not (:allow-resume? reg-params))
    [:duplicate :no-resume]

    (or (nil? (:group existing-user))
        (not (= (:group reg-params) (:group existing-user))))
    [:duplicate :group-mismatch]

    :else
    (let [identical-sms?   (= (:sms-number existing-user) (:sms-number reg-fields))
          identical-email? (= (:email existing-user) (:email reg-fields))
          identical-pid?   (= (:pid-number existing-user) (:pid-number reg-fields))
          match-sms?       (not (:allow-duplicate-sms? reg-params))
          match-email?     (not (:allow-duplicate-email? reg-params))
          match-pid?       (and (:bankid? reg-params)
                                (not (:allow-duplicate-bankid? reg-params)))
          fails            (concat
                             (when (and match-sms?
                                        (not identical-sms?))
                               [:sms-mismatch])
                             (when (and match-email?
                                        (not identical-email?))
                               [:email-mismatch])
                             (when (and match-pid?
                                        (not identical-pid?))
                               [:pid-mismatch]))]
      (when-not (some true? [match-sms? match-email? match-pid?])
        (throw (ex-info "No unique identifier but :allow-resume? true anyway" (or reg-params {}))))
      (if (seq fails)
        [:duplicate (into #{} fails)]
        [:resume :ok]))))

(defn- handle-resume
  [project-id session reg-params user]
  (let [user-id  (:user-id user)
        password (when (or (:auto-password? reg-params)
                           (contains? (:fields reg-params) :password))
                   (let [password (if (:auto-password? reg-params)
                                    (passwords/password)
                                    (if-let [password (get-in session [:registration :field-values :password])]
                                      password
                                      (throw (ex-info "Password not present in registration" (:registration session)))))]
                     (user-service/update-user-properties! user-id {:password password} "resume registration")
                     password))]
    (if (not (empty? (:username user)))
      (->
        (http-response/found (str "/registration/" project-id "/credentials"))
        (assoc-reg-session session {:resume?     true
                                    :credentials (merge
                                                   {:user-id user-id :username (:username user)}
                                                   (when (:auto-password? reg-params) {:password password}))}))
      (->
        (http-response/found (str "/registration/" project-id "/finished"))
        (assoc-reg-session session {:resume?     true
                                    :credentials {:user-id user-id}})))))

(defn- handle-duplicates
  [project-id session reg-params duplicate-ids]
  (let [[action _ user] (if (< 1 (count duplicate-ids))
                          [:duplicate :too-many]
                          (let [user   (user-service/get-user (first duplicate-ids))
                                fields (get-in session [:registration :field-values])]
                            (conj (resolve-duplicate user fields reg-params) user)))]
    (if (= :duplicate action)
      (-> (http-response/found (str "/registration/" project-id "/duplicate"))
          (reset-reg-session session))
      (handle-resume project-id session reg-params user))))


(defn- duplicate-conflict?
  [field-values reg-params]
  (when-let [duplicates-map (merge
                              (when (not (:allow-duplicate-email? reg-params))
                                (select-keys field-values [:email]))
                              (when (not (:allow-duplicate-sms? reg-params))
                                (select-keys field-values [:sms-number]))
                              (when (and (:bankid? reg-params)
                                         (not (:allow-duplicate-bankid? reg-params)))
                                (select-keys field-values [:pid-number])))]
    (reg-service/duplicate-participants duplicates-map)))


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
      (http-response/found (str "/registration/" project-id "/credentials"))
      (assoc-reg-session session {:credentials (merge
                                                 {:user-id user-id :username username}
                                                 (if auto-password? {:password password}))}))
    (->
      (http-response/found (str "/registration/" project-id "/finished"))
      (assoc-reg-session session {:credentials {:user-id user-id}}))))

(defn- create-user
  [project-id field-values reg-params session]
  (let [participant-id (gen-participant-id project-id reg-params)
        username       (gen-username field-values participant-id reg-params)
        field-values   (gen-password field-values reg-params)
        user-id        (reg-service/create-user!
                         project-id
                         field-values
                         (get-in session [:registration :privacy-consent])
                         (get-in session [:registration :study-consent])
                         username
                         participant-id
                         (:group reg-params))]
    (-> (created-redirect
          project-id
          user-id
          username
          (:password field-values)
          (:auto-password? reg-params)
          session))))

(defn- complete-registration
  [project-id field-values reg-params session]
  (if-let [duplicates (duplicate-conflict? field-values reg-params)]
    (handle-duplicates project-id session reg-params duplicates)
    (create-user project-id field-values reg-params session)))

;; ---------------
;;     CAPTCHA
;; ---------------

(defn- captcha-session
  [project-id session]
  (let [{:keys [filename digits]} (captcha/captcha!)]
    (-> (http-response/found (str "/registration/" project-id "/captcha"))
        (assoc-reg-session session {:captcha-filename  filename
                                    :captcha-digits    digits
                                    :captcha-timestamp (now/now)
                                    :captcha-tries     0}))))

(defn- captcha-page
  [project-id filename digits]
  (let [content (reg-service/captcha-content project-id)]
    (render-page project-id
                 "registration-captcha.html"
                 (merge
                   content
                   {:project-id project-id
                    :filename   filename}
                   (when (clients/debug-mode?)
                     {:digits digits})))))

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
      (let [time-elapsed (t/in-seconds (t/interval timestamp (now/now)))]
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

(defapi captcha
  [project-id :- api/->int session :- [:? map?]]
  (if (get-in session [:registration :captcha-ok?])
    (http-response/found (str "/registration/" project-id))
    (let [{:keys [filename digits]} (current-captcha session)]
      (if (and filename digits)
        (captcha-page project-id filename digits)
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
    (-> (http-errors/error-422 "error")
        (assoc :session new-session))
    (-> (http-response/found (str "/registration/" project-id "/captcha"))
        (assoc :session new-session))))

(defapi validate-captcha
  [project-id :- api/->int captcha :- [[api/str? 1 10]] session :- [:? map?]]
  (if-let [digits (captcha-digits session)]
    (if (= digits captcha)
      ;; TODO: Remove captcha values
      (-> (http-response/found (str "/registration/" project-id "/privacy"))
          (assoc-reg-session session {:captcha-ok? true}))
      (wrong-captcha-response project-id (inc-tries session)))
    (http-response/found (str "/registration/" project-id "/captcha"))))

;; --------------
;;   VALIDATION
;; --------------

(def validation-code-length 5)

(defn send-email!
  [email code]
  (mail/async-email! db/*db*
                     email
                     (i18n/tr [:registration/validation-code])
                     (str (i18n/tr [:registration/validation-code]) " " code)))

(defn- send-sms!
  [sms-number code]
  (sms/async-sms! db/*db* sms-number (str (i18n/tr [:registration/validation-code]) " " code)))

(defn- code-map
  [code-key field-key send-fn! field-values fixed-fields validation-codes]
  (when-not (contains? fixed-fields field-key)
    (when-let [address (get field-values field-key)]
      ;; Only send new code if code hasn't already been sent to email/sms
      (if (= (get-in validation-codes [code-key :address]) address)
        (select-keys validation-codes [code-key])
        (let [code (passwords/letters-digits validation-code-length)]
          (send-fn! address code)
          {code-key {:address address
                     :code    code}})))))

(defn- prepare-validation
  [project-id field-values session]
  (let [reg-session      (:registration session)
        fixed-fields     (:fixed-fields reg-session)
        validation-codes (:validation-codes reg-session)
        codes            (merge
                           (code-map :code-sms :sms-number send-sms! field-values fixed-fields validation-codes)
                           (code-map :code-email :email send-email! field-values fixed-fields validation-codes)
                           ;; New uid for the newly sent codes is created each time.
                           {:uid (UUID/randomUUID)})]
    (when (and (nil? (:code-sms codes)) (nil? (:code-email codes)))
      (throw (ex-info "Prepare validation did not render codes" reg-session)))
    (->
      (http-response/found (str "/registration/" project-id "/validate"))
      (assoc-reg-session session {:field-values     field-values
                                  :validation-codes codes}))))

(defn render-validation-page
  [project-id codes fixed-fields field-values]
  (render-page project-id
               "registration-validation.html"
               (merge
                 {:email       (when (and
                                       (contains? codes :code-email)
                                       (not (contains? fixed-fields :email)))
                                 (:email field-values))
                  :sms-number  (when (and
                                       (contains? codes :code-sms)
                                       (not (contains? fixed-fields :sms-number)))
                                 (:sms-number field-values))
                  :project-id  project-id
                  :code-length validation-code-length}
                 (when (clients/debug-mode?)
                   codes))))

(defapi validation-page
  [project-id :- api/->int session :- [:? map?]]
  (let [reg-session  (:registration session)
        field-values (:field-values reg-session)
        fixed-fields (:fixed-fields reg-session)
        codes        (:validation-codes reg-session)]
    (render-validation-page project-id codes fixed-fields field-values)))

(def validated-codes (atom (cache/ttl-cache-factory {} :ttl (* 1000 60 60 24))))

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
    (when (= (string/trim posted-code) (get-in validation-codes [code-key :code]))
      (code-validated! uid code-key)
      true)))

(defn- all-codes-validated?
  [uid validation-codes]
  (= (get @validated-codes uid) (set/difference (set (keys validation-codes)) #{:uid})))

(defn validate-code
  [project-id code-key posted-code session reg-params]
  (let [reg-session      (:registration session)
        field-values     (:field-values reg-session)
        validation-codes (:validation-codes reg-session)
        uid              (:uid validation-codes)]
    (let [correct? (and (string? posted-code) (code-correct?! uid code-key validation-codes posted-code))]
      ;; Are all outstanding codes validated?
      ;; Already validated codes are not present in map
      (if (all-codes-validated? uid validation-codes)
        (complete-registration
          project-id
          field-values
          reg-params
          session)
        (if correct?
          (let [fixed-fields (:fixed-fields reg-session)
                field-name   (if (= :code-email code-key)
                               :email
                               :sms-number)]
            (-> (http-response/ok "ok")
                (assoc-reg-session session {:fixed-fields (set/union fixed-fields #{field-name})})))
          (http-errors/error-422 "error"))))))

(defapi validate-email
  [project-id :- api/->int posted-code :- [[api/str? 1 10]] session :- [:? map?] reg-params :- map?]
  (validate-code project-id :code-email posted-code session reg-params))

(defapi validate-sms
  [project-id :- api/->int posted-code :- [[api/str? 1 10]] session :- [:? map?] reg-params :- map?]
  (validate-code project-id :code-sms posted-code session reg-params))


;; --------------
;;     BANKID
;; --------------
(defn bankid-done?
  [session]
  (let [e-auth (:e-auth session)]
    (and e-auth (every? e-auth [:personnummer :first-name :last-name]))))

(defapi bankid-page
  [project-id :- api/->int]
  (let [reg-content (reg-service/registration-content project-id)]
    (render-page project-id
                 "registration-bankid.html"
                 (merge
                   reg-content
                   {:project-id project-id}))))

(defn get-bankid-fields
  [session params]
  (when (:bankid? params)
    (let [e-auth (:e-auth session)]
      {:field-values {:pid-number (:personnummer e-auth)
                      :first-name (:first-name e-auth)
                      :last-name  (:last-name e-auth)}
       :fixed-fields (set/union
                       #{:pid-number}
                       (when-not (:bankid-change-names? params)
                         #{:first-name :last-name}))})))

(defapi bankid-finished
  [project-id :- api/->int session :- [:? map?] reg-params :- map?]
  (if (bankid-done? session)
    (let [bankid-fields (get-bankid-fields session reg-params)]
      (->
        (http-response/found (str "/registration/" project-id "/privacy"))
        (assoc-reg-session session (merge bankid-fields
                                          {:bankid-done? true}))
        (assoc-in [:session :e-auth] nil)))
    (throw (ex-info "BankID returned incomplete complete info" {:e-auth session}))))

(defapi bankid-poster
  [project-id :- api/->int personnummer :- [[api/str? 1 30]] request]
  (if (e-auth/personnummer-valid? personnummer)
    (e-auth/launch-bankid
      request
      personnummer
      (str "/registration/" project-id "/bankid-finished")
      (str "/registration/" project-id "/bankid"))
    (http-response/bad-request (str "Personnummer does not have valid format " personnummer))))

;; ----------------
;;   REGISTRATION
;; ----------------

(defn merge-fields-with-field-vals
  [fields field-values fixed-fields]
  (merge (if (seq fixed-fields)
           (apply dissoc fields fixed-fields)
           fields)
         (zipmap (mapv #(keyword (str (name %) "-value")) (keys field-values))
                 (vals field-values))))

(defapi registration-page
  [project-id :- api/->int session :- [:? map?]]
  (let [reg-content   (reg-service/registration-content project-id)
        reg-session   (:registration session)
        fields        (:fields reg-content)
        fields-map    (zipmap fields (repeat (count fields) true))
        fields-map    (merge-fields-with-field-vals
                        fields-map
                        (:field-values reg-session)
                        (:fixed-fields reg-session))
        sms-countries (str "[\"" (string/join "\",\"" (:sms-countries reg-content)) "\"]")]
    ;; TODO: Show country for fixed sms number
    (render-page project-id
                 "registration-form.html"
                 (merge
                   reg-content
                   fields-map
                   {:project-id     project-id
                    :sms-countries  sms-countries
                    :sms?           (or (contains? fields-map :sms-number)
                                        (contains? fields-map :sms-number-value))
                    :password-regex passwords/password-regex
                    :pid-name       (if (:bankid? reg-content)
                                      (i18n/tr [:registration/personnummer])
                                      (:pid-name reg-content))}))))

(defn- check-sms
  [sms-number sms-countries]
  (cond
    (nil? sms-number)
    true

    (not (string/starts-with? sms-number "+"))
    false

    :else
    (let [matching (-> (select-keys reg-service/country-codes sms-countries)
                       vals
                       flatten)]
      (some
        #(string/starts-with?
           sms-number
           (str "+" (get % "callingCode")))
        matching))))

(defn- password-valid?
  [field-values]
  (if (:password field-values)
    (passwords/password-valid? (:password field-values))
    true))

(defapi handle-registration
  [project-id :- api/->int posted-fields :- map? session :- [:? map?] reg-params :- map?]
  (let [fields       (:fields reg-params)
        reg-session  (:registration session)
        fixed-fields (:fixed-fields reg-session)
        field-values (merge (select-keys posted-fields fields)
                            (select-keys (:field-values reg-session) fixed-fields))]
    (cond
      ;; All required fields should be present
      (not (all-fields? fields field-values))
      (http-response/bad-request "Missing required field")

      ;; No fixed fields should have been posted
      (not (empty? (set/intersection fixed-fields (set (keys posted-fields)))))
      (http-response/bad-request "Fixed field posted")

      ;; If password has been posted - must be valid
      (not (password-valid? field-values))
      (http-response/bad-request "Invalid password")

      ;; Only sms number from legal countries
      (not (check-sms (:sms-number field-values) (:sms-countries reg-params)))
      (http-errors/error-422 "sms-country-error")

      ;; If field values include email or sms - these should be validated
      ;; before registration is complete.
      (or (contains? field-values :sms-number) (contains? field-values :email))
      (prepare-validation project-id field-values session)

      :else
      (complete-registration
        project-id
        field-values
        reg-params
        session))))


;; -----------------
;;   STUDY CONSENT
;; -----------------
(defapi study-consent-page
  [project-id :- api/->int]
  (let [consent-text (:consent-text (reg-service/registration-study-consent project-id))]
    (render-page project-id
                 "registration-study-consent.html"
                 {:project-id   project-id
                  :consent-text consent-text})))

(defapi handle-study-consent
  [project-id :- api/->int i-consent :- [[api/str? 1 20]] session :- [:? map?]]
  (let [consent-text (reg-service/registration-study-consent project-id)]
    (if-not (and (:consent-text consent-text) (= "i-consent" i-consent))
      (http-response/bad-request)
      (->
        (http-response/found (str "/registration/" project-id "/form"))
        (assoc-reg-session session {:study-consent {:consent-id (:consent-id consent-text)
                                                    :time       (now/now)}})))))

;; --------------
;;    PRIVACY
;; --------------
(defapi privacy-page
  [project-id :- api/->int]
  (let [privacy-notice (privacy-service/get-privacy-notice project-id)]
    (render-page project-id
                 "registration-privacy-notice.html"
                 {:project-id     project-id
                  :privacy-notice (:notice-text privacy-notice)})))

(defapi handle-privacy-consent
  [project-id :- api/->int i-consent :- [[api/str? 1 20]] session :- [:? map?]]
  (let [privacy-notice (privacy-service/get-privacy-notice project-id)]
    (if-not (and (:notice-text privacy-notice) (= "i-consent" i-consent))
      (http-response/bad-request)
      (->
        (http-response/found (str "/registration/" project-id "/form"))
        (assoc-reg-session session {:privacy-consent {:notice-id (:notice-id privacy-notice)
                                                      :time      (now/now)}})))))


;; --------------
;;     CANCEL
;; --------------

(defapi cancel-registration
  [project-id :- api/->int session :- [:? map?]]
  (-> (http-response/found (str "/registration/" project-id))
      (reset-reg-session session)))


;; --------------
;;   INFO PAGE
;; --------------
(defapi info-page
  [project-id :- api/->int]
  (let [reg-params (reg-service/registration-content project-id)]
    (render-page project-id
                 "registration-info.html"
                 (merge
                   {:project-id project-id}
                   reg-params))))

;; --------------
;;   LOGGED IN
;; --------------
(defapi logged-in-page
  []
  (layout/render "registration-logged-in.html"))


;; TODO: Max number of sms