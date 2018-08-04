(ns bass4.services.bass
  (:require [bass4.db.core :as db]
            [bass4.db-config :as db-config]
            [clj-time.core :as t]
            [bass4.config :refer [env]]
            [clojure.tools.logging :as log]
            [bass4.utils :refer [map-map-keys str->int json-safe]]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.data.json :as json])
  (:import (java.util UUID)
           (java.io File)))

(defn db-title []
  (:title (db/get-db-title)))

(defn db-sms-sender []
  (:sms-sender (db/get-sms-sender)))

(defn db-contact-info
  ([] (db-contact-info 0))
  ([project-id]
   (let [emails (db/get-contact-info {:project-id project-id})]
     (assoc emails :email (if-not (empty? (:project-email emails))
                            (:project-email emails)
                            (:db-email emails))))))

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
    (t/time-zone-for-id (db-config/time-zone))
    (catch Exception e
      (log/error "Time zone illegal: " (db-config/time-zone))
      (t/default-time-zone))))

(defn local-midnight
  ([] (local-midnight (t/now)))
  ([date-time]
   (t/with-time-at-start-of-day (t/to-time-zone date-time (time-zone)))))

(defn get-sub-path
  "Makes sure that the combination of
  base-path and sub-path is within base-path"
  [base-path sub-path]
  (let [full-sub-path (io/file (.getCanonicalPath (io/file base-path sub-path)))]
    (when (string/starts-with? (str full-sub-path) (str base-path))
      full-sub-path)))

(defn db-dir
  (^File [base-path] (db-dir base-path nil))
  (^File [base-path sub-path]
   (try
     (let [db-name        (:name db-config/*local-config*)
           full-base-path (io/file (env :bass-path) "projects" db-name base-path)]
       (if sub-path
         (get-sub-path full-base-path sub-path)
         full-base-path))
     (catch Exception e))))

(defn- check-file-age
  [file max-age-sec]
  (or (not max-age-sec)
      (> max-age-sec (/ (- (. System (currentTimeMillis))
                           (.lastModified file))
                        1000))))

(defn read-session-file
  ([filename] (read-session-file filename false nil))
  ([filename delete? max-age-sec]
   (when-not (or (nil? filename) (s/includes? filename "/"))
     (when-let [file (db-dir "sessiondata" filename)]
       (when (and (.exists file) (check-file-age file max-age-sec))
         (let [info (json-safe (slurp file) keyword)]
           ;; TODO: Check if session is ongoing in BASS
           (when delete?
             (io/delete-file file))
           info))))))

(defn write-session-file
  ([contents] (write-session-file contents nil))
  ([contents prefix]
   (let [filename (str (when prefix (str prefix "_")) (UUID/randomUUID))
         file     (db-dir "sessiondata" filename)]
     (spit file (json/write-str contents))
     filename)))

(defn remove-leading-slash
  [x]
  (if (= "/" (subs x 0 1))
    (subs x 1)
    x))

(defn uploaded-file
  ^File
  [filename]
  (when (not (empty? filename))
    (when-let [file (db-dir "upload" (remove-leading-slash filename))]
      (when (.exists file)
        file))))

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