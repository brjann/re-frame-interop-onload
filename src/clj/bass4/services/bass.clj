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
            [bass4.clients :as clients]
            [clojure.core.cache :as cache])
  (:import (java.util UUID)
           (java.io File)))

(defn db-title []
  (:title (db/get-db-title)))

(defn db-sms-sender [db]
  (:sms-sender (db/get-sms-sender db {})))

(defn db-url
  [db]
  (:url (db/get-db-url db {})))

(defn db-contact-info*
  ([db project-id]
   (let [emails (db/get-contact-info {:project-id project-id})]
     (assoc emails :email (if-not (empty? (:project-email emails))
                            (:project-email emails)
                            (:db-email emails))))))

(defn db-contact-info
  ([] (db-contact-info 0))
  ([project-id]
   (db-contact-info* db/*db* project-id)))

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
    (t/time-zone-for-id (clients/db-setting [:timezone]))
    (catch Exception e
      (log/error "Time zone illegal: " (clients/db-setting [:timezone]))
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
     (let [db-name        (:name clients/*local-config*)
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
;
;(defonce uids (atom (cache/ttl-cache-factory {} :ttl (* 1000 60 60 24))))
;
;(defn uid-for-data!
;  [data]
;  (let [uid (UUID/randomUUID)]
;    ))