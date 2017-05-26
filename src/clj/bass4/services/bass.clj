(ns bass4.services.bass
  (:require [bass4.db.core :as db]
            [clj-time.core :as t]
            [clojure.tools.logging :as log]
            [clj-time.coerce :as tc]))

(defn db-title []
  (:title (db/get-db-title)))

;;; TODO: It does not return on form 'object-id'
(defn create-bass-objects-without-parent!
  [class-name, property-name, count]
  (let [last-object-id (:objectid (db/create-bass-objects-without-parent!
                                    {:class-name class-name
                                     :property-name property-name
                                     :count count}))]
    (range (inc (- last-object-id count)) (inc last-object-id))))

(def ^:dynamic *time-zone* "America/Puerto_Rico")

(defn time-zone
  []
  (try
    (t/time-zone-for-id *time-zone*)
    (catch Exception e
      (log/error "Time zone illegal: " *time-zone*)
      (t/default-time-zone))))

(defn local-midnight
  []
  (t/with-time-at-start-of-day (t/to-time-zone (t/now) (time-zone))))

(defn init-repl
    ([] (init-repl :db1))
    ([db-name]
     (alter-var-root (var db/*db*) (constantly @(get-in db/*dbs* [db-name :db-conn])))
     (alter-var-root (var *time-zone*) (constantly (or (get-in db/*dbs* [db-name :db-time-zone]) *time-zone*)))))