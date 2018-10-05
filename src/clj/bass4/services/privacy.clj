(ns bass4.services.privacy
  (:require [bass4.db.core :as db]
            [clojure.string :as s]))

(defn- trim-sql-notice
  [privacy-notice]
  (let [privacy-notice (some-> privacy-notice
                               (:notice-text)
                               (s/trim))]
    (when-not (empty? privacy-notice)
      privacy-notice)))

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
  (some-> (db/bool-cols
            db/project-privacy-user-must-consent?
            {:project-id project-id}
            [:must-consent?])
          (first)
          (val)))

(defn db-user-must-consent?
  []
  (some-> (db/bool-cols
            db/db-privacy-user-must-consent?
            [:must-consent?])
          (first)
          (val)))

(defn user-must-consent?
  ([] (db-user-must-consent?))
  ([project-id]
   (or (db-user-must-consent?)
       (project-user-must-consent? project-id))))