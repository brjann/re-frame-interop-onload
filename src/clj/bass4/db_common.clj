(ns bass4.db-common
  (:require [ring.util.codec :refer [url-encode]]
            [bass4.utils :refer [map-map parse-php-constants filter-map]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [bass4.config :as config]
            [mount.core :refer [defstate]]
            [clojure.tools.logging :as log]
            [clojure.set :as set]))


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

(defn- load-common-config
  [bass-path]
  (-> (io/file bass-path "local.php")
      slurp
      (parse-php-constants)
      fix-keys
      (assoc :name "common")))

(defstate common-config
  :start (load-common-config (config/env :bass-path)))