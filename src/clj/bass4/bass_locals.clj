(ns bass4.bass-locals
  (:require [ring.util.codec :refer [url-encode]]
            [bass4.utils :refer [map-map parse-php-constants]]))

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
  (->> (.listFiles (clojure.java.io/file bass-path))
       (filter #(clojure.string/starts-with? (.getName %) "local_"))))

(defn- parse-local [file]
    (let [db-name (last (re-find #"local_(.*?).php" (.getName file)))]
      {(keyword db-name) (parse-php-constants (slurp file))}))

;; TODO: Do Latin1 connections need to be handled?
(defn- build-db-url
  ([port config]
   (when (every? config [:DB_NAME :DB_PWD :DB_USER :DB_HOST])
     (let [{name :DB_NAME password :DB_PWD user :DB_USER host :DB_HOST} config]
       (str "jdbc:mysql://" host
            ":" port
            "/" (url-encode name)
            "?user=" (url-encode user)
            "&password=" (url-encode password))))))

(defn- db-config [port config]
  (when-let [db-url (build-db-url port config)]
    {:db-url db-url :time-zone (:DB_TIME_ZONE config) :lang (:LANGUAGE config)}))

(defn get-bass-db-configs
  ([bass-path] (get-bass-db-configs bass-path 3306))
  ([bass-path port]
   (let [configs (reduce merge (map parse-local (get-locals bass-path)))]
     (->> configs
          (map-map (partial db-config port))
          (filter #(val %))
          (into {})))))