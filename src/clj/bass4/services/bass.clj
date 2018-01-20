(ns bass4.services.bass
  (:require [bass4.db.core :as db]
            [bass4.bass-locals :as locals]
            [clj-time.core :as t]
            [bass4.config :refer [env]]
            [clojure.tools.logging :as log]
            [bass4.utils :refer [map-map-keys str->int json-safe]]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defn db-title []
  (:title (db/get-db-title)))

(defn db-sms-sender []
  (:sms-sender (db/get-sms-sender)))

;;; TODO: It does not return on form 'object-id'
(defn create-bass-objects-without-parent!
  [class-name, property-name, count]
  (let [last-object-id (:objectid (db/create-bass-objects-without-parent!
                                    {:class-name    class-name
                                     :property-name property-name
                                     :count         count}))]
    (range (inc (- last-object-id count)) (inc last-object-id))))

(defn time-zone
  []
  (try
    (t/time-zone-for-id (locals/time-zone))
    (catch Exception e
      (log/error "Time zone illegal: " (locals/time-zone))
      (t/default-time-zone))))

(defn local-midnight
  ([] (local-midnight (t/now)))
  ([date-time]
   (t/with-time-at-start-of-day (t/to-time-zone date-time (time-zone)))))

#_(defn db-dir
    ([] (db-dir nil))
    ([sub-dir]
     (let [db-name   (:name locals/*local-config*)
           bass-path (env :bass-path)]
       (io/file bass-path "projects" db-name sub-dir))))

#_(defn db-dir
    ^java.io.File
    [& parts]
    (let [db-name   (:name locals/*local-config*)
          bass-path (env :bass-path)]
      (try
        (apply io/file (into [bass-path "projects" db-name] parts))
        (catch Exception e))))

(defn get-sub-path
  "Makes sure that the combination of
  base-path and sub-path is within base-path"
  [base-path sub-path]
  (let [full-sub-path (io/file (.getCanonicalPath (io/file base-path sub-path)))]
    (when (string/starts-with? (str full-sub-path) (str base-path))
      full-sub-path)))

(defn db-dir
  (^java.io.File [base-path] (db-dir base-path nil))
  (^java.io.File [base-path sub-path]
   (try
     (let [db-name        (:name locals/*local-config*)
           full-base-path (io/file (env :bass-path) "projects" db-name base-path)]
       (if sub-path
         (get-sub-path full-base-path sub-path)
         full-base-path))
     (catch Exception e))))

#_(defn- session-dir
    []
    (let [db-name   (:name locals/*local-config*)
          bass-path (env :bass-path)]
      (io/file bass-path "projects" db-name "sessiondata")))

(defn embedded-session-file
  [filename]
  (when-not (or (nil? filename) (s/includes? filename "/"))
    (let [file (db-dir "sessiondata" filename)]
      (when (.exists file)
        (let [info (json-safe (slurp file) keyword)]
          ;; TODO: Check if session is ongoing in BASS
          #_(io/delete-file file)
          info)))))

(defn remove-leading-slash
  [x]
  (if (= "/" (subs x 0 1))
    (subs x 1)
    x))


(defn uploaded-file
  ^java.io.File
  [filename]
  (when filename
    (when-let [file (db-dir "upload" (remove-leading-slash filename))]
      (when (.exists file)
        file))))

#_(defn uid-file
    ^string
    [uid]
    (cond
      (not (string? uid)) nil
      (.contains uid "/")) nil
    (try
      (let [uid-no-slash (string/replace (or uid "") "/" "")
            file         (io/file (env :bass-path) "projects/temp" uid-no-slash)]
        (when (and file (.exists file))
          file))
      (catch Exception e)))

(defn uid-file
  [uid]
  (cond
    (not (string? uid)) nil
    (.contains uid "/") nil
    :else
    (try
      (let [file (io/file (env :bass-path) "projects/temp" uid)]
        (when (.exists file)
          file))
      (catch Exception e))))