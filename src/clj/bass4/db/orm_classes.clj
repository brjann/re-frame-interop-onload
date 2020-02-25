(ns bass4.db.orm-classes
  (:require [bass4.utils :as utils]
            [bass4.db.core :as db]))


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

(defn create-bass-object*!
  [db class-name parent-id property-name]
  (:objectid (db/create-bass-object! db {:class-name    class-name
                                         :parent-id     parent-id
                                         :property-name property-name})))

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
