(ns bass4.client-config
  (:require [mount.core :refer [defstate]]
            [bass4.db.core :as db]
            [bass4.config :as config]))

(def local-defaults
  {:time-zone "America/Puerto_Rico"
   :language  "en"})

(def ^:dynamic *local-config*
  local-defaults)

(defstate local-configs
  :start (do db/client-db-configs))

(defn time-zone
  []
  (:time-zone *local-config*))

(defn language
  []
  (:language *local-config*))

(defn client-name
  []
  (:name *local-config*))

(defn db-setting*
  [db-name setting-keys default]
  (let [setting (let [x (get-in config/env (into [:db-settings db-name] setting-keys))]
                  (if-not (nil? x)
                    x
                    (let [x (get-in config/env setting-keys)]
                      (if-not (nil? x)
                        x
                        default))))]
    #_(when (nil? setting)
        (throw (Exception. (str "No setting found for " setting-keys ". No default provided"))))
    setting))

(defn db-setting
  ([setting-keys] (db-setting setting-keys nil))
  ([setting-keys default]
   (db-setting* (keyword (client-name)) setting-keys default)))

(defn debug-mode?
  []
  (or (db-setting [:debug-mode] false) (:dev config/env)))
