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
  (reduce merge (map parse-local (get-locals bass-path))))

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