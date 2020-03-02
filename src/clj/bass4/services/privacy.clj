(ns bass4.services.privacy
  (:require [bass4.db.core :as db]
            [clojure.string :as s]
            [bass4.utils :as utils]))

(defn- trim-sql-notice
  [privacy-notice]
  (let [notice-text (some-> privacy-notice
                            (:notice-text)
                            (s/trim))]
    (when-not (empty? notice-text)
      {:notice-id   (:id privacy-notice)
       :notice-text notice-text})))

(defn- get-project-privacy-notice
  [project-id]
  (trim-sql-notice (db/get-project-privacy-notice {:project-id project-id})))

(defn- get-db-privacy-notice
  []
  (trim-sql-notice (db/get-db-privacy-notice)))

(defn get-privacy-notice
  ([] (get-db-privacy-notice))
  ([project-id]
   (or (get-project-privacy-notice project-id)
       (get-db-privacy-notice))))

(defn project-user-must-consent?
  [project-id]
  (some-> (db/project-privacy-user-must-consent? {:project-id project-id})
          (first)
          (val)))

(defn db-user-must-consent?
  []
  (some-> (db/db-privacy-user-must-consent?)
          (first)
          (val)))

(defn ^:dynamic user-must-consent?
  ([] (db-user-must-consent?))
  ([project-id]
   (or (db-user-must-consent?)
       (project-user-must-consent? project-id))))

(defn ^:dynamic privacy-notice-exists?
  [project-id]
  (-> (db/privacy-notice-exists? {:project-id project-id})
      (:exists?)
      (utils/val-to-bool)))

(defn ^:dynamic privacy-notice-disabled?
  []
  (-> (:disabled? (db/privacy-notice-disabled?))))