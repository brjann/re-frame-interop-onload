(ns bass4.db-config
  (:require [ring.util.codec :refer [url-encode]]
            [bass4.utils :refer [map-map parse-php-constants filter-map]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [bass4.config :as config]
            [mount.core :refer [defstate]]
            [clojure.tools.logging :as log]
            [clojure.set :as set]))

(def local-defaults
  {:time-zone "America/Puerto_Rico"
   :language  "en"})

(def ^:dynamic *local-config*
  local-defaults)

(defn time-zone
  []
  (:time-zone *local-config*))

(defn language
  []
  (:language *local-config*))

(defn- get-locals [bass-path]
  (->> (.listFiles (io/file bass-path))
       (filter #(clojure.string/starts-with? (.getName %) "local_"))))

;; Please note that :db-name and :name are not the same!
;; :db-name is the name of MySQL database
;; :name    is the name of the BASS database/client, i.e. local_xxx.php <- xxx = name
(defn db-name
  []
  (:name *local-config*))

(defn db-setting
  ([setting-keys] (db-setting setting-keys nil))
  ([setting-keys default]
   (let [db-name (keyword (db-name))
         setting (or (get-in config/env (into [:db-settings db-name] setting-keys))
                     (get-in config/env setting-keys)
                     default)]
     (when (nil? setting)
       (throw (Exception. (str "No setting found for " setting-keys ". No default provided"))))
     setting)))

(defn debug-mode?
  []
  (or (db-setting [:debug-mode] false) (:dev config/env)))

(defn- check-keys
  [local-config]
  (set/subset? #{:db-host :db-name :db-user :db-password} (set (keys local-config))))

(defn- fix-keys
  [m]
  (zipmap (mapv #(-> %
                     name
                     string/lower-case
                     (string/replace "_" "-")
                     (string/replace "pwd" "password")
                     (string/replace "db-time-zone" "time-zone")
                     (string/replace "db-common-" "db-")
                     keyword)
                (keys m)) (vals m)))

(defn- parse-local [file]
  (let [db-name (last (re-find #"local_(.*?).php" (.getName file)))]
    {(keyword db-name)
     (-> (slurp file)
         (parse-php-constants)
         fix-keys
         (assoc :name db-name))}))

(defn- load-local-configs
  [bass-path]
  (->> (get-locals bass-path)
       (map parse-local)
       (reduce merge)
       (filter-map check-keys)))

(defn- load-common-config
  [bass-path]
  (-> (io/file bass-path "local.php")
      slurp
      (parse-php-constants)
      fix-keys
      (assoc :name "common")))

(defstate local-configs
  :start (load-local-configs (config/env :bass-path)))

(defstate common-config
  :start (load-common-config (config/env :bass-path)))