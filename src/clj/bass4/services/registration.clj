(ns bass4.services.registration
  (:require [bass4.db.core :as db]
            [bass4.php_clj.core :refer [php->clj]]
            [bass4.services.user :as user]
            [bass4.utils :refer [filter-map map-map]]
            [clojure.tools.logging :as log]))

(defn registration-allowed?
  [project-id]
  (:allowed (db/bool-cols db/registration-allowed? {:project-id project-id} [:allowed])))

(defn captcha-content
  [project-id]
  (:captcha-content (db/captcha-content {:project-id project-id})))

(defn registration-content
  [project-id]
  (:registration-content (db/registration-content {:project-id project-id})))

(defn registration-params
  [project-id]
  (let [params         (db/registration-params {:project-id project-id})
        fields-mapping (->> (php->clj (:fields-mapping params))
                            (into {})
                            (filter-map #(not (= "" %)))
                            (map-map keyword))
        group          (#(if (or (nil? %) (zero? %)) nil %) (:group params))]
    {:fields-mapping fields-mapping :group group}))

(defn create-user!
  [project-id field-values group]
  (user/create-user! project-id (merge field-values (when group {:group group}))))