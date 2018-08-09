(ns bass4.services.privacy
  (:require [bass4.db.core :as db]
            [clojure.string :as s]))

(defn- trim-sql-notice
  [privacy-notice]
  (let [privacy-notice (some-> privacy-notice
                               (:privacy-notice)
                               (s/trim))]
    (when-not (empty? privacy-notice)
      privacy-notice)))

(defn get-project-privacy-notice
  [project-id]
  (trim-sql-notice (db/get-project-privacy-notice {:project-id project-id})))

(defn get-db-privacy-notice
  []
  (trim-sql-notice (db/get-db-privacy-notice)))

(defn get-privacy-notice
  ([] (get-privacy-notice nil))
  ([project-id]
   (or (get-project-privacy-notice project-id)
       (get-db-privacy-notice))))