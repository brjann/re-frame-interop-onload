(ns bass4.services.registration
  (:require [bass4.db.core :as db]
            [bass4.php-clj.safe :refer [php->clj]]
            [bass4.utils :refer [filter-map map-map in? subs+]]
            [clojure.tools.logging :as log]
            [clojure.set :as set]
            [clojure.string :as string]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [bass4.time :as b-time]
            [bass4.services.user :as user-service]))

(defn captcha-content
  [project-id]
  (db/captcha-content {:project-id project-id}))

(defn finished-content
  [project-id]
  (db/finished-content {:project-id project-id}))

(def field-translation
  {:FirstName    :first-name
   :LastName     :last-name
   :Email        :email
   :SMSNumber    :sms-number
   :Personnummer :pid-number
   ;; Must be lowercase for password hasher to recognize
   :Password     :password})

(defn- transform-fields
  [fields-str]
  (->> (php->clj fields-str)
       (filter-map identity)
       (keys)
       (map keyword)
       (map #(get field-translation %))
       (into #{})))

(defn- consolidate-params
  [params]
  (let [auto-username    (case (:auto-username params)
                           "participantid" :participant-id
                           "email" :email
                           :none)
        fields           (-> (transform-fields (:fields params)))
        fields           (if (= auto-username :email)
                           (conj fields :email)
                           fields)
        fields           (if (:bankid? params)
                           (clojure.set/union
                             fields
                             #{:pid-number :first-name :last-name})
                           fields)
        auto-password?   (cond
                           (contains? fields :password) false
                           (not= auto-username :none) true
                           :else (:auto-password? params))
        duplicate-email? (if (= auto-username :email)
                           false
                           (:allow-duplicate-email? params))
        auto-id?         (if (= auto-username :participant-id)
                           true
                           (:auto-id? params))]
    ;; pid-validator is replaced by swedish validator later if bankid? is true
    (merge params
           {:auto-username          auto-username
            :auto-id?               auto-id?
            :fields                 fields
            :auto-password?         auto-password?
            :allow-duplicate-email? duplicate-email?})))

(defn registration-params
  [project-id]
  (if-let [params (db/registration-params {:project-id project-id})]
    (let [params        (consolidate-params params)
          sms-countries (mapv string/lower-case (string/split-lines (:sms-countries params)))]
      (merge
        params
        {:fields        (:fields params)
         :sms-countries sms-countries}))))

(defn registration-content
  [project-id]
  (let [params        (db/registration-content {:project-id project-id})
        fields        (transform-fields (:fields params))
        group         (#(if (or (nil? %) (zero? %)) nil %) (:group params))
        sms-countries (mapv string/lower-case (string/split-lines (:sms-countries params)))]
    (merge
      {:fields fields :group group :sms-countries sms-countries}
      (select-keys params [:pid-name :pid-format :pid-validator :info :markdown? :bankid? :bankid-change-names?]))))

(defn registration-study-consent
  [project-id]
  (db/registration-study-consent {:project-id project-id}))

(defn create-user!
  [project-id field-values privacy-consent study-consent username participant-id group]
  (let [insert-values (filter-map identity (map-map #(get field-values %) field-translation))]
    (user-service/create-user! project-id (merge insert-values
                                                 (when privacy-consent
                                                   {:PrivacyNoticeId          (:notice-id privacy-consent)
                                                    :PrivacyNoticeConsentTime (b-time/to-unix (:time privacy-consent))})
                                                 (when study-consent
                                                   {:StudyConsentId   (:consent-id study-consent)
                                                    :StudyConsentTime (b-time/to-unix (:time study-consent))})
                                                 (when username {:username username})
                                                 (when participant-id {:participantid participant-id})
                                                 (when group {:group group})))))

(defn duplicate-info?
  [{:keys [email sms-number]}]
  (->> (db/check-duplicate-info
         {:email      (or email "_")
          :sms-number (or sms-number "_")})
       :count
       (< 0)))

(defn- next-auto-id
  [project-id]
  (jdbc/with-db-transaction [conn db/*db*]
    (let [id (:auto-id (db/get-current-auto-id-for-update conn {:project-id project-id}))]
      (db/increment-auto-id! conn {:project-id project-id})
      id))
  #_(let [id (:auto-id (db/get-current-auto-id-for-update {:project-id project-id}))]
      (db/increment-auto-id! {:project-id project-id})
      id))

(defn generate-participant-id
  [project-id prefix length]
  (let [id             (str (next-auto-id project-id))
        zeroes         (string/join (repeat length "0"))
        participant-id (str prefix (subs+ zeroes 0 (- length (count id))) id)]
    (if (zero? (:count (db/check-participant-id {:participant-id participant-id})))
      participant-id
      (recur project-id prefix length))))