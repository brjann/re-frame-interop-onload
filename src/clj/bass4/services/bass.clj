(ns bass4.services.bass
  (:require [bass4.db.core :as db]
            [clj-time.core :as t]
            [bass4.config :refer [env]]
            [clojure.tools.logging :as log]
            [bass4.utils :refer [map-map-keys str->int json-safe]]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.data.json :as json]
            [bass4.utils :as utils]
            [bass4.client-config :as client-config])
  (:import (java.util UUID)
           (java.io File)))

(defn db-title []
  (:title (db/get-db-title)))

(defn db-sms-sender [db]
  (:sms-sender (db/get-sms-sender db {})))

(defn db-url
  [db]
  (:url (db/get-db-url db {})))

(defn db-contact-info
  ([] (db-contact-info 0))
  ([project-id]
   (let [emails (db/get-contact-info {:project-id project-id})]
     (assoc emails :email (if-not (empty? (:project-email emails))
                            (:project-email emails)
                            (:db-email emails))))))

(defn create-bass-objects-without-parent*!
  [db class-name property-name count]
  (when (not (utils/nil-zero? count))
    (let [last-object-id (:objectid (db/create-bass-objects-without-parent!
                                      db
                                      {:class-name    class-name
                                       :property-name property-name
                                       :count         count}))]
      (range (inc (- last-object-id count)) (inc last-object-id)))))

;;; TODO: It does not return on form 'object-id'
(defn create-bass-objects-without-parent!
  [class-name property-name count]
  (create-bass-objects-without-parent*! db/*db* class-name property-name count))

(defn update-object-properties*!
  "Allows for updating object properties using strings as field names to
  avoid cluttering the keyword namespace."
  [db table-name object-id updates]
  (db/update-object-properties! db
                                {:table-name table-name
                                 :object-id  object-id
                                 :updates    (into {}
                                                   (map (fn [[k v]] [(keyword k) v])
                                                        updates))}))

(defn update-object-properties!
  "Allows for updating object properties using strings as field names to
  avoid cluttering the keyword namespace."
  [table-name object-id updates]
  (update-object-properties*! db/*db* table-name object-id updates))

(defn set-objectlist-parent!
  [db object-id parent-id]
  (db/update-objectlist-parent!
    db
    {:object-id object-id
     :parent-id parent-id}))

(defn create-flag!
  ([user-id issuer flag-text] (create-flag! user-id issuer flag-text ""))
  ([user-id issuer flag-text flag-icon]
   (let [flag-id (:objectid (db/create-bass-object! {:class-name    "cFlag"
                                                     :parent-id     user-id
                                                     :property-name "Flags"}))]
     (db/update-object-properties! {:table-name "c_flag"
                                    :object-id  flag-id
                                    :updates    {:FlagText   flag-text
                                                 :CustomIcon flag-icon
                                                 :Open       1
                                                 :Issuer     issuer
                                                 :ClosedAt   0}})
     flag-id)))

(declare local-midnight)

(defn- inc-external-message-count!
  [db-connection type]
  (when db-connection
    (let [midnight (-> (local-midnight)
                       (utils/to-unix))]
      (db/inc-external-message-count!
        db-connection
        {:type type
         :day  midnight}))))

(defn inc-sms-count!
  [db-connection]
  (inc-external-message-count! db-connection "sms"))

(defn inc-email-count!
  [db-connection]
  (inc-external-message-count! db-connection "email"))

(defn time-zone
  []
  (try
    (t/time-zone-for-id (client-config/db-setting [:timezone]))
    (catch Exception e
      (log/error "Time zone illegal: " (client-config/db-setting [:timezone]))
      (t/default-time-zone))))

(defn local-midnight
  ([] (local-midnight (t/now)))
  ([date-time]
   (local-midnight date-time (time-zone)))
  ([date-time time-zone]
   (t/with-time-at-start-of-day (t/to-time-zone date-time time-zone))))

(defn get-php-session
  [php-session-id]
  (db/get-php-session {:php-session-id php-session-id}))

(defn update-php-session-last-activity!
  [php-session-id now]
  (db/update-php-session-last-activity! {:php-session-id php-session-id :now now}))

(defn get-staff-timeouts
  []
  (db/get-staff-timeouts))

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
     (let [db-name        (:name client-config/*local-config*)
           full-base-path (io/file (env :bass-path) "projects" db-name base-path)]
       (if sub-path
         (get-sub-path full-base-path sub-path)
         full-base-path))
     (catch Exception _))))

(defn check-file-age
  [file max-age-sec]
  (or (not max-age-sec)
      (> max-age-sec (/ (- (. System (currentTimeMillis))
                           (.lastModified file))
                        1000))))

(defn read-session-file
  ([filename] (read-session-file filename false nil))
  ([filename delete? max-age-sec]
   (when-not (or (empty? filename) (s/includes? filename "/"))
     (when-let [file (db-dir "sessiondata" filename)]
       (when (and (.exists file) (.isFile file) (check-file-age file max-age-sec))
         (try
           (let [info (json-safe (slurp file) keyword)]
             (when delete?
               (io/delete-file file))
             info)
           (catch Exception _)))))))

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