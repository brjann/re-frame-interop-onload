(ns bass4.clients.core
  (:require [mount.core :refer [defstate]]
            [bass4.db.core :as db]
            [bass4.config :as config]
            [bass4.utils :as utils]
            [clojure.tools.logging :as log]
            [bass4.db-common :as db-common]))

(def ^:dynamic *client-config* {})

(def client-defaults
  {:timezone "Europe/Stockholm"
   :language "en"})

(defstate client-configs
  :start (let [configs (db/load-client-db-configs db/db-common)]
           (->> configs
                (map #(assoc % :name (:id-name %)))
                (map #(if (empty? (:timezone %))
                        (assoc % :timezone (:timezone client-defaults))
                        %))
                (map #(if (empty? (:language %))
                        (assoc % :language (:language client-defaults))
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

(defstate client-db-connections
  :start (let [x (->> client-configs
                      (utils/map-map connect-db)
                      (utils/filter-map identity))]
           (when (config/env :dev)
             (log/info "Setting *db* to dev database")
             (alter-var-root #'db/*db* (constantly @(get x (config/env :dev-db)))))
           x)
  :stop (dorun (map (fn [[name conn]]
                      (db/db-disconnect! conn name))
                    client-db-connections)))

(defn client-setting*
  [client-name-kw setting-keys default]
  (or
    (get-in client-configs (concat [client-name-kw] setting-keys))
    (let [setting (let [x (get-in config/env (into [:db-settings client-name-kw] setting-keys))]
                    (if-not (nil? x)
                      x
                      (let [x (get-in config/env setting-keys)]
                        (if-not (nil? x)
                          x
                          default))))]
      setting)))

(defn client-setting
  ([setting-keys] (client-setting setting-keys nil))
  ([setting-keys default]
   (if (= [:name] setting-keys)
     (:name *client-config*)
     (client-setting* (keyword (:name *client-config*)) setting-keys default))))

(defn db->client-name
  [db]
  (let [[client-name _] (->>
                          client-db-connections
                          (filter #(= db @(second %)))
                          (first))]
    client-name))

(defn client-host
  [db]
  (:bass4-host (get client-configs (db->client-name db))))

(defn debug-mode?
  []
  (or (client-setting [:debug-mode] false) (:dev config/env)))

(defn sms-config
  [db]
  (let [client-name  (db->client-name db)
        sms-settings (client-setting* client-name [:sms-settings] nil)]
    (if sms-settings
      sms-settings
      (let [config db-common/common-config]
        (assoc
          (select-keys config [:smsteknik-id :smsteknik-user :smsteknik-password])
          :provider :sms-teknik)))))
