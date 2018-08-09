(ns bass4.services.privacy
  (:require [bass4.db.core :as db]
            [buddy.hashers :as hashers]
            [bass4.config :as config]
            [clojure.string :as s]))

(defn get-project-privacy-notice
  [project-id]
  (some-> (db/get-project-privacy-notice {:project-id project-id})
          (:privacy-notice)
          (s/trim)))

(defn get-db-privacy-notice
  []
  (some-> (db/get-db-privacy-notice)
          (:privacy-notice)
          (s/trim)))

(defn get-privacy-notice
  [project-id]
  (let [privacy-notice (get-project-privacy-notice project-id)]
    (if (empty? privacy-notice)
      (get-db-privacy-notice)
      privacy-notice)))