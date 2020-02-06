(ns bass4.clients
  (:require [mount.core :refer [defstate]]
            [bass4.db.core :as db]
            [bass4.config :as config]
            [bass4.utils :as utils]
            [clojure.tools.logging :as log]))

(def ^:dynamic *local-config* {})

(def local-defaults
  {:timezone "Europe/Stockholm"
   :language "en"})

(defstate local-configs
  :start (let [configs (db/load-client-db-configs db/db-common)]
           (->> configs
                (map #(assoc % :name (:id-name %)))
                (map #(if (empty? (:timezone %))
                        (assoc % :timezone (:timezone local-defaults))
                        %))
                (map #(if (empty? (:language %))
                        (assoc % :language (:language local-defaults))
                        %))
                (map (juxt (comp keyword :id-name) identity))
                (into {}))))

(defn- connect-db
  [db-config]
  (let [conn (try
               (db/db-connect! db-config)
               (catch Throwable e
                 (log/error "Could not connect to db because of:" (:id-name db-config) (:cause (Throwable->map e)))))]
    (if conn
      (delay conn))))

(defstate db-connections
  :start (let [x (->> local-configs
                      (utils/map-map connect-db)
                      (utils/filter-map identity))]
           (when (config/env :dev)
             (log/info "Setting *db* to dev database")
             (alter-var-root #'db/*db* (constantly @(get x (config/env :dev-db)))))
           x)
  :stop (dorun (map (fn [[name conn]]
                      (db/db-disconnect! conn name))
                    db-connections)))

(defn db-setting*
  [client-name-kw setting-keys default]
  (or
    (get-in local-configs (concat [client-name-kw] setting-keys))
    (let [setting (let [x (get-in config/env (into [:db-settings client-name-kw] setting-keys))]
                    (if-not (nil? x)
                      x
                      (let [x (get-in config/env setting-keys)]
                        (if-not (nil? x)
                          x
                          default))))]
      setting)))

(defn db-setting
  ([setting-keys] (db-setting setting-keys nil))
  ([setting-keys default]
   (if (= [:name] setting-keys)
     (:name *local-config*)
     (db-setting* (keyword (:name *local-config*)) setting-keys default))))

(defn debug-mode?
  []
  (or (db-setting [:debug-mode] false) (:dev config/env)))
