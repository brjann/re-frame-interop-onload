(ns bass4.services.bass
  (:require [bass4.db.core :as db]))

(defn project-title []
  (:title (db/get-project-title)))

;;; TODO: It does not return on form 'object-id'
(defn create-bass-objects-without-parent!
  [class-name, property-name, count]
  (let [last-object-id (:objectid (db/create-bass-objects-without-parent!
                                    {:class-name class-name
                                     :property-name property-name
                                     :count count}))]
    (range (inc (- last-object-id count)) (inc last-object-id))))
