(ns bass4.services.registration
  (:require [bass4.db.core :as db]
            [bass4.php_clj.core :refer [php->clj]]
            [bass4.services.user :as user]
            [bass4.utils :refer [filter-map map-map in?]]
            [clojure.tools.logging :as log]
            [clojure.set :as set]
            [clojure.string :as string]))

(defn registration-allowed?
  [project-id]
  (:allowed (db/bool-cols db/registration-allowed? {:project-id project-id} [:allowed])))

(defn captcha-content
  [project-id]
  (db/bool-cols db/captcha-content {:project-id project-id} [:markdown?]))

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
                           "email" :email)
        fields           (-> (transform-fields (:fields params)))
        fields           (if (= auto-username :email)
                           (conj fields :email)
                           fields)
        auto-password?   (cond
                           (contains? fields :password) false
                           (not= auto-username :none) true
                           :else (:auto-password? params))
        duplicate-email? (if (= auto-username :email)
                           false
                           (:allow-duplicate-email? params))]
    (merge params
           {:auto-username          auto-username
            :fields                 fields
            :auto-password?         auto-password?
            :allow-duplicate-email? duplicate-email?})))

(defn registration-params
  [project-id]
  (let [params        (consolidate-params (db/bool-cols
                                            db/registration-params
                                            {:project-id project-id}
                                            [:allow-duplicate-email? :allow-duplicate-sms? :auto-id? :auto-password?]))
        sms-countries (mapv string/lower-case (string/split-lines (:sms-countries params)))]
    (merge
      params
      {:fields        (:fields params)
       :sms-countries sms-countries})))

(defn registration-content
  [project-id]
  (let [params        (db/registration-content {:project-id project-id})
        fields        (transform-fields (:fields params))
        group         (#(if (or (nil? %) (zero? %)) nil %) (:group params))
        sms-countries (mapv string/lower-case (string/split-lines (:sms-countries params)))]
    (merge
      {:fields fields :group group :sms-countries sms-countries}
      (select-keys params [:pid-name :pid-format :pid-validator :info :markdown?]))))

(defn create-user!
  [project-id field-values group]
  (let [insert-values (filter-map identity (map-map #(get field-values %) field-translation))]
    (user/create-user! project-id (merge insert-values (when group {:group group})))))

(defn duplicate-info?
  [{:keys [email sms-number]}]
  (->> (db/check-duplicate-info
         {:email      (or email "_")
          :sms-number (or sms-number "_")})
       :count
       (< 0)))