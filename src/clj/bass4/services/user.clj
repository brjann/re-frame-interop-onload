(ns bass4.services.user
  (:require [bass4.db.core :as db]))

(defn get-user [user-id]
  (when user-id
    (if-let [user (db/bool-cols db/get-user-by-user-id {:user-id user-id} [:double-auth-use-both?])]
      (assoc user :user-id (:objectid user)))))

(defn get-users-by-participant-id [participant-id]
  (when-let [users (db/bool-cols db/get-user-by-participant-id {:participant-id participant-id} [:double-auth-use-both?])]
    (mapv #(assoc % :user-id (:objectid %)) users)))

(defn support-email [user]
  (:email (db/bool-cols db/get-support-email {:project-id (:project-id user)} [:double-auth-use-both?])))

(defn update-user-properties!
  [user-id properties]
  (db/update-user-properties! {:user-id user-id :updates properties}))

(defn create-user!
  ([project-id] (create-user! [project-id nil]))
  ([project-id properties]
   (let [collection-id (:collection-id (db/get-project-participant-collection {:project-id project-id}))
         user-id       (:objectid (db/create-bass-object! {:class-name    "cParticipant"
                                                           :parent-id     collection-id
                                                           :property-name "Participants"}))]
     (when properties
       (update-user-properties! user-id properties))
     user-id)))

