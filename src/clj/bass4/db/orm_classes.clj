(ns bass4.db.orm-classes
  (:require [bass4.utils :as utils]
            [bass4.db.core :as db]))

(def ^:dynamic *created-objects* nil)

(defn create-bass-objects-without-parent*!
  [db class-name property-name count]
  (when (not (utils/nil-zero? count))
    (let [last-object-id (:objectid (db/create-bass-objects-without-parent!
                                      db
                                      {:class-name    class-name
                                       :property-name property-name
                                       :count         count}))
          new-ids        (range (inc (- last-object-id count)) (inc last-object-id))]
      (when *created-objects*
        (swap! *created-objects* into new-ids))
      new-ids)))

(defn create-bass-objects-without-parent!
  [class-name property-name count]
  (create-bass-objects-without-parent*! db/*db* class-name property-name count))

(defn create-bass-object-map!
  ([map] (create-bass-object-map! db/*db* map))
  ([db map] (if *created-objects*
              (let [id (:objectid (db/create-bass-object! db map))]
                (swap! *created-objects* conj id)
                {:objectid id})
              (db/create-bass-object! db map))))

(defn create-bass-object*!
  [db class-name parent-id property-name]
  (:objectid (create-bass-object-map! db {:class-name    class-name
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

(defn create-link!
  [linker-id linkee-id link-property linker-class linkee-class]
  (db/create-bass-link! {:linker-id     linker-id
                         :linkee-id     linkee-id
                         :link-property link-property
                         :linker-class  linker-class
                         :linkee-class  linkee-class}))

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
  ([user-id issuer flag-text] (create-flag! user-id issuer flag-text {}))
  ([user-id issuer flag-text properties]
   (let [flag-id (:objectid (create-bass-object-map! {:class-name    "cFlag"
                                                      :parent-id     user-id
                                                      :property-name "Flags"}))]
     (update-object-properties! "c_flag"
                                flag-id
                                (merge
                                  {"FlagText"   flag-text
                                   "CustomIcon" ""
                                   "Open"       1
                                   "Issuer"     issuer
                                   "ClosedAt"   0}
                                  properties))
     flag-id)))
