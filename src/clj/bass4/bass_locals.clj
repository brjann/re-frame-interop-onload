(ns bass4.bass-locals
  (:require [ring.util.codec :refer [url-encode]]))

(def regex-local (let [q "(\"[^\"\\\\]*(\\\\(.|\\n)[^\"\\\\]*)*\"|'[^'\\\\]*(\\\\(.|\\n)[^'\\\\]*)*')"]
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

(defn- parse-local [file]
  (let [db-name (last (re-find #"local_(.*?).php" (.getName file)))]
    (let [matcher (re-matcher regex-local (slurp file) )]
      (hash-map (keyword db-name) (parse-local-rec matcher)))))

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

(defn get-bass-db-configs
  ([bass-path] (get-bass-db-configs bass-path 3306))
  ([bass-path port]
   (let [configs (reduce merge (map parse-local (get-locals bass-path)))]
     (into {} (filter #(not (nil? (val %))) (zipmap (keys configs) (map (partial build-db-url port) (vals configs))))))))
