(ns bass4.services.bass
  (:require [bass4.db.core :as db]
            [bass4.bass-locals :as locals]
            [clj-time.core :as t]
            [bass4.config :refer [env]]
            [clojure.tools.logging :as log]
            [bass4.utils :refer [map-map-keys str->int]]
            [clj-time.coerce :as tc]
            [clojure.string :as s]
            [clojure.java.io :as io]))

(defn db-title []
  (:title (db/get-db-title)))

(defn db-sms-sender []
  (:sms-sender (db/get-sms-sender)))

;;; TODO: It does not return on form 'object-id'
(defn create-bass-objects-without-parent!
  [class-name, property-name, count]
  (let [last-object-id (:objectid (db/create-bass-objects-without-parent!
                                    {:class-name class-name
                                     :property-name property-name
                                     :count count}))]
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

(defn- session-dir
  []
  (let [db-name   (:name locals/*db-config*)
        bass-path (env :bass-path)]
    (io/file bass-path "projects" db-name "sessiondata")))

(defn admin-session-file
  [filename]
  (when-not (s/includes? filename "/")
    (let [file (io/file (session-dir) filename)]
      (when (.exists file)
        (let [user-id (str->int (slurp file))]
          (io/delete-file file)
          user-id)))))

#_(defn init-repl
    ([] (init-repl :db1))
    ([db-name]
     (alter-var-root (var db/*db*) (constantly @(get-in db/db-configs [db-name :db-conn])))
     (alter-var-root (var *time-zone*) (constantly (or (get-in db/db-configs [db-name :db-time-zone]) *time-zone*)))))