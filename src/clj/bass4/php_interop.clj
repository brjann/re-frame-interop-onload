(ns bass4.php-interop
  (:require [bass4.db.core :as db]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [bass4.clients.core :as clients]
            [bass4.config :as config]
            [clojure.data.json :as json]
            [bass4.utils :as utils]
            [clojure.core.cache :as cache]
            [bass4.passwords :as passwords])
  (:import (java.io File)
           (java.util UUID)))

(defn ^:dynamic get-php-session
  [php-session-id]
  (db/get-php-session {:php-session-id php-session-id}))

(defn update-php-session-last-activity!
  [php-session-id now]
  (db/update-php-session-last-activity! {:php-session-id php-session-id :now now}))

(defn get-admin-timeouts
  []
  (db/get-admin-timeouts))

(defn check-php-session
  [timeouts {:keys [user-id php-session-id]}]
  (if-let [php-session (get-php-session php-session-id)]
    (let [last-activity      (:last-activity php-session)
          php-user-id        (:user-id php-session)
          now-unix           (utils/current-time)
          time-diff-activity (- now-unix last-activity)
          re-auth-timeout    (:re-auth-timeout timeouts)
          absolute-timeout   (:absolute-timeout timeouts)]
      (cond
        (not= user-id php-user-id)
        ::user-mismatch

        (>= time-diff-activity absolute-timeout)
        ::absolute-timeout

        (>= time-diff-activity re-auth-timeout)
        ::re-auth-timeout

        :else
        ::ok))
    ::no-session))

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
     (let [db-name        (:name clients/*client-config*)
           full-base-path (io/file (config/env :bass-path) "projects" db-name base-path)]
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
   (when-not (or (empty? filename) (string/includes? filename "/"))
     (when-let [file (db-dir "sessiondata" filename)]
       (when (and (.exists file) (.isFile file) (check-file-age file max-age-sec))
         (try
           (let [info (utils/json-safe (slurp file) keyword)]
             (when delete?
               (io/delete-file file))
             info)
           (catch Exception _)))))))

;; TODO: Only used by extlogin - can be replaced with UID atom
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
      (let [file (io/file (config/env :bass-path) "projects/temp" uid)]
        (when (.exists file)
          file))
      (catch Exception e))))


;; ---------------
;;   NEW UID API
;; ---------------

;; UIDs are valid for 24 hours
(defonce uids (atom (cache/ttl-cache-factory {} :ttl (* 24 1000 60 60))))

(defn uid-for-data!
  [data]
  (let [uid (passwords/letters-digits 36 passwords/url-safe-chars)]
    (swap! uids assoc uid data)
    uid))

(defn add-data-to-uid!
  "Adds data to uid using merge-with into."
  [uid data]
  (swap! uids (fn [a] (when (contains? a uid)
                        (let [content (get a uid)]
                          (assoc a uid (merge-with into
                                                   content
                                                   data)))))))

(defn data-for-uid!
  [uid]
  (let [[old _] (swap-vals! uids dissoc uid)]
    (get old uid)))