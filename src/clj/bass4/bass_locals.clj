(ns bass4.bass-locals
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

(defn parse-local [file]
    (let [db-name (last (re-find #"local_(.*?).php" (.getName file)))]
      {(keyword db-name) (assoc (parse-php-constants (slurp file)) :name db-name)}))

(defn- build-db-url
  [host port name user password]
  (str "jdbc:mysql://" host
       ":" port
       "/" (url-encode name)
       "?user=" (url-encode user)
       "&password=" (url-encode password)))

;; TODO: Do Latin1 connections need to be handled?
(defn- db-url
  [port config]
  (when (every? config [:DB_NAME :DB_PWD :DB_USER :DB_HOST])
    (let [{name :DB_NAME password :DB_PWD user :DB_USER host :DB_HOST} config]
      (build-db-url host port name user password))))

(defn db-config [port config]
  (when-let [db-url (db-url port config)]
    {:db-url db-url :time-zone (:DB_TIME_ZONE config) :lang (:LANGUAGE config) :name (:name config)}))

(defn get-bass-db-configs
  ([bass-path] (get-bass-db-configs bass-path 3306))
  ([bass-path port]
   (let [configs (reduce merge (map parse-local (get-locals bass-path)))
         common (parse-php-constants (slurp (io/file bass-path "local.php")))]
     (assoc (->> configs
                 (map-map (partial db-config port))
                 (filter #(val %))
                 (into {}))
       :common (merge common {:db-url (build-db-url
                                        (:DB_COMMON_HOST common)
                                        port
                                        (:DB_COMMON_NAME common)
                                        (:DB_COMMON_USER common)
                                        (:DB_COMMON_PWD common))})))))

(defn check-keys
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

(defn parse-local-2 [file]
  (let [db-name (last (re-find #"local_(.*?).php" (.getName file)))]
    {(keyword db-name)
     (-> (slurp file)
         (parse-php-constants)
         fix-keys
         (assoc :name db-name))}))

(defn load-local-configs
  [bass-path]
  (reduce merge (map parse-local-2 (get-locals bass-path))))

(defn load-local-configs
  [bass-path]
  (->> (get-locals bass-path)
       (map parse-local-2)
       (reduce merge)
       (filter-map check-keys)))

(defstate local-configs
  :start (load-local-configs (config/env :bass-path)))

(defn load-common-config
  [bass-path]
  (-> (io/file bass-path "local.php")
      slurp
      (parse-php-constants)
      fix-keys
      (assoc :name "common")))

(defstate common-config
  :start (load-common-config (config/env :bass-path)))