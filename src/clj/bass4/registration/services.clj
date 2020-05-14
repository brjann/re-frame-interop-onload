(ns bass4.registration.services
  (:require [bass4.db.core :as db]
            [bass4.php-clj.safe :refer [php->clj]]
            [bass4.utils :refer [filter-map map-map in? subs+]]
            [clojure.tools.logging :as log]
            [clojure.set :as set]
            [clojure.string :as string]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [bass4.clients.time :as client-time]
            [bass4.services.user :as user-service]
            [bass4.utils :as utils]
            [clojure.pprint :as pprint]
            [clojure.java.io :as io]))

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
        fields           (if (= :none auto-username)
                           (disj fields :password)
                           fields)
        fields           (if (:bankid? params)
                           (clojure.set/union
                             fields
                             #{:pid-number :first-name :last-name})
                           fields)
        auto-password?   (cond
                           (contains? fields :password) false
                           (not= :none auto-username) true
                           :else false)
        duplicate-email? (if (= auto-username :email)
                           false
                           (:allow-duplicate-email? params))
        auto-id?         (if (= auto-username :participant-id)
                           true
                           (:auto-id? params))
        group            (when-not (utils/nil-zero? (:group params))
                           (:group params))
        allow-resume?    (cond
                           (not (:allow-resume? params))
                           false

                           (not group)
                           false

                           (not (or (and (contains? fields :email)
                                         (not duplicate-email?))
                                    (and (contains? fields :sms-number)
                                         (not (:allow-duplicate-sms? params)))
                                    (and (:bankid? params)
                                         (not (:allow-duplicate-bankid? params)))))
                           false

                           :else true)]
    ;; pid-validator is replaced by swedish validator later if bankid? is true
    (merge params
           {:auto-username          auto-username
            :auto-id?               auto-id?
            :fields                 fields
            :group                  group
            :auto-password?         auto-password?
            :allow-duplicate-email? duplicate-email?
            :allow-resume?          allow-resume?})))

(defn ^:dynamic db-registration-params
  [project-id]
  (db/registration-params {:project-id project-id}))

(def country-codes
  (group-by #(string/lower-case (get % "code")) (utils/json-safe (slurp (io/resource "docs/country-calling-codes.json")))))

(defn sms-countries
  [params]
  (or (->> (string/split-lines (:sms-countries params))
           (mapv string/lower-case)
           (filter #(contains? country-codes %))
           (not-empty))
      ["se"]))

(defn ^:dynamic registration-params
  [project-id]
  (if-let [params (db-registration-params project-id)]
    (let [params (consolidate-params params)]
      (merge
        params
        {:fields        (:fields params)
         :sms-countries (sms-countries params)}))))

(defn registration-content
  [project-id]
  (let [params (db/registration-content {:project-id project-id})
        fields (transform-fields (:fields params))
        group  (#(if (or (nil? %) (zero? %)) nil %) (:group params))]
    (merge
      {:fields fields :group group :sms-countries (sms-countries params)}
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
                                                    :PrivacyNoticeConsentTime (utils/to-unix (:time privacy-consent))})
                                                 (when study-consent
                                                   {:StudyConsentId   (:consent-id study-consent)
                                                    :StudyConsentTime (utils/to-unix (:time study-consent))})
                                                 (when username {:username username})
                                                 (when participant-id {:participantid participant-id})
                                                 (when group {:group group})))))

(defn duplicate-participants
  [{:keys [email sms-number pid-number]}]
  (->> (db/check-duplicates
         {:email      (or email "%€#&()")
          :sms-number (or sms-number "%€#&()")
          :pid-number (or pid-number "%€#&()")})
       (map :user-id)
       (seq)))

(defn- next-auto-id
  [project-id]
  (jdbc/with-db-transaction [conn db/*db*]
    (let [id (:auto-id (db/get-current-auto-id-for-update conn {:project-id project-id}))]
      (db/increment-auto-id! conn {:project-id project-id})
      id)))

(defn ^:dynamic generate-participant-id
  [project-id prefix length]
  (let [id             (str (next-auto-id project-id))
        zeroes         (string/join (repeat length "0"))
        participant-id (str prefix (subs+ zeroes 0 (- length (count id))) id)]
    (if (zero? (:count (db/check-participant-id {:participant-id participant-id})))
      participant-id
      (recur project-id prefix length))))