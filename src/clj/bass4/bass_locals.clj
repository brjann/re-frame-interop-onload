(ns bass4.bass-locals
  (:require [ring.util.codec :refer [url-encode]]
            [bass4.utils :refer [map-map parse-php-constants]]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]))

(def db-defaults
  {:time-zone "America/Puerto_Rico"
   :language "se"})

(def ^:dynamic *db-config*
  db-defaults)

(defn time-zone
  []
  (:time-zone *db-config*))

(defn language
  []
  (:language *db-config*))

(defn- get-locals [bass-path]
  (->> (.listFiles (io/file bass-path))
       (filter #(clojure.string/starts-with? (.getName %) "local_"))))

(defn parse-local [file]
    (let [db-name (last (re-find #"local_(.*?).php" (.getName file)))]
      {(keyword db-name) (parse-php-constants (slurp file))}))

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
    {:db-url db-url :time-zone (:DB_TIME_ZONE config) :lang (:LANGUAGE config)}))

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