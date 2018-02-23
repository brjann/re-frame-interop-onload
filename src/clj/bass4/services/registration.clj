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
  (:captcha-content (db/captcha-content {:project-id project-id})))

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
  (let [content (db/registration-content {:project-id project-id})]
    (merge content {:fields (transform-fields (:fields content))})))

(defn registration-params
  [project-id]
  (let [params        (db/registration-params {:project-id project-id})
        fields        (transform-fields (:fields params))
        group         (#(if (or (nil? %) (zero? %)) nil %) (:group params))
        sms-countries (string/split-lines (:sms-countries params))]
    (merge
      {:fields fields :group group :sms-countries sms-countries}
      (select-keys params [:pid-name :pid-format]))))

(defn create-user!
  [project-id field-values group]
  (user/create-user! project-id (merge field-values (when group {:group group}))))