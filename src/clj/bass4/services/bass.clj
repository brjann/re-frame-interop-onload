(ns bass4.services.bass
  (:require [bass4.db.core :as db]))

(defn db-title []
  (:title (db/get-db-title)))

(defn db-time-zone []
  (:time-zone (db/get-db-time-zone)))

;;; TODO: It does not return on form 'object-id'
(defn create-bass-objects-without-parent!
  [class-name, property-name, count]
  (let [last-object-id (:objectid (db/create-bass-objects-without-parent!
                                    {:class-name class-name
                                     :property-name property-name
                                     :count count}))]
    (range (inc (- last-object-id count)) (inc last-object-id))))

(def ^:dynamic *time-zone* "America/Puerto_Rico")