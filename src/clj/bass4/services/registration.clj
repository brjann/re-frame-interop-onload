(ns bass4.services.registration
  (:require [bass4.db.core :as db]
            [bass4.php-clj.safe :refer [php->clj]]
            [bass4.services.user :as user]
            [bass4.utils :refer [filter-map map-map in? subs+]]
            [clojure.tools.logging :as log]
            [clojure.set :as set]
            [clojure.string :as string]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [bass4.time :as b-time]))

(defn registration-allowed?
  [project-id]
  (:allowed (db/bool-cols db/registration-allowed? {:project-id project-id} [:allowed])))

(defn captcha-content
  [project-id]
  (db/bool-cols db/captcha-content {:project-id project-id} [:markdown?]))

(defn finished-content
  [project-id]
  (db/bool-cols db/finished-content {:project-id project-id} [:markdown?]))

(def field-translation
  {:FirstName    :first-name
   :LastName     :last-name
   :Email        :email
   :SMSNumber    :sms-number
   :Personnummer :pid-number
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
  (if-let [params (db/bool-cols
                    db/registration-params
                    {:project-id project-id}
                    [:allowed?
                     :allow-duplicate-email?
                     :allow-duplicate-sms?
                     :auto-id?
                     :auto-password?
                     :bankid?
                     :bankid-change-names?])]
    (let [params        (consolidate-params params)
          sms-countries (mapv string/lower-case (string/split-lines (:sms-countries params)))]
      (merge
        params
        {:fields        (:fields params)
         :sms-countries sms-countries}))))

(defn registration-content
  [project-id]
  (let [params        (db/bool-cols
                        db/registration-content
                        {:project-id project-id}
                        [:allowed?
                         :bankid?
                         :bankid-change-names?])
        fields        (transform-fields (:fields params))
        group         (#(if (or (nil? %) (zero? %)) nil %) (:group params))
        sms-countries (mapv string/lower-case (string/split-lines (:sms-countries params)))]
    (merge
      {:fields fields :group group :sms-countries sms-countries}
      (select-keys params [:pid-name :pid-format :pid-validator :info :markdown? :bankid? :bankid-change-names?]))))

(defn create-user!
  [project-id field-values privacy-consent username participant-id group]
  (let [insert-values (filter-map identity (map-map #(get field-values %) field-translation))]
    (user/create-user! project-id (merge insert-values
                                         {:PrivacyNotice            (:privacy-notice privacy-consent)
                                          :PrivacyNoticeConsentTime (b-time/to-unix (:time privacy-consent))}
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