(ns bass4.bass-locals
  (:require [ring.util.codec :refer [url-encode]]
            [bass4.utils :refer [map-map]]))

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

(def regex-php-constant (let [q "(\"[^\"\\\\]*(\\\\(.|\\n)[^\"\\\\]*)*\"|'[^'\\\\]*(\\\\(.|\\n)[^'\\\\]*)*')"]
                   (re-pattern (str "define\\s*\\(\\s*" q "\\s*,\\s*" q "\\s*\\)\\s*;"))))

(defn- get-locals [bass-path]
  (->> (.listFiles (clojure.java.io/file bass-path))
       (filter #(clojure.string/starts-with? (.getName %) "local_"))))

(defn- un-escape [str]
  (-> (subs str 1 (dec (count str)))
      (clojure.string/replace  #"\\([^\\])" "$1")
      (clojure.string/replace  "\\\\" "\\")))

(defn- parse-local-rec [matcher]
  (let [match (re-find matcher)]
    (when match
      (assoc (parse-local-rec matcher) (keyword (un-escape (nth match 1))) (un-escape (nth match 6))))))

(defn- parse-php-constants
  [text]
  (let [matcher (re-matcher regex-php-constant text)]
    (parse-local-rec matcher)))

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