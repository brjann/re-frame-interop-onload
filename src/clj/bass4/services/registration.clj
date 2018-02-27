(ns bass4.services.registration
  (:require [bass4.db.core :as db]
            [bass4.php_clj.core :refer [php->clj]]
            [bass4.services.user :as user]
            [bass4.utils :refer [filter-map map-map]]
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
       (map #(get field-translation %))))

(defn registration-params
  [project-id]
  (let [params        (db/registration-params {:project-id project-id})
        sms-countries (mapv string/lower-case (string/split-lines (:sms-countries params)))]
    (merge
      params
      {:fields        (transform-fields (:fields params))
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