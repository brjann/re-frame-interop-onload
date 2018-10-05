(ns bass4.services.user
  (:require [bass4.db.core :as db]
            [buddy.hashers :as hashers]
            [bass4.config :as config]
            [bass4.time :as b-time]
            [clj-time.core :as t]))

(defn get-user
  [user-id]
  (when user-id
    (if-let [user (db/bool-cols db/get-user-by-user-id {:user-id user-id} [:double-auth-use-both?])]
      (assoc user :user-id (:objectid user)))))

(defn get-user-by-username [username]
  (db/get-user-by-username {:username username}))

(defn get-users-by-participant-id
  [participant-id]
  (when-let [users (db/bool-cols db/get-user-by-participant-id {:participant-id participant-id} [:double-auth-use-both?])]
    (mapv #(assoc % :user-id (:objectid %)) users)))

(defn password-hasher
  [password]
  (let [algo (config/env :password-hash)]
    (hashers/derive password algo)))

(defn upgrade-password!
  [user]
  (if-not (empty? (:old-password user))
    (do
      (when-not (empty? (:password user))
        (throw (Exception. (str "User " (:user-id user) " has both new and old password"))))
      (let [password-hash (password-hasher (:old-password user))]
        (db/update-password! {:user-id (:user-id user) :password password-hash})
        (assoc user :password password-hash)))
    user))

(defn update-user-properties!
  [user-id properties]
  (let [properties (if (:password properties)
                     (let [password-hash (-> (:password properties)
                                             (str)
                                             (password-hasher))]
                       (assoc properties :password password-hash))
                     properties)]
    (db/update-user-properties! {:user-id user-id :updates properties})))

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

#_(defn set-user-privacy-consent!
  [user-id privacy-notice now]
  (update-user-properties!
    user-id
    {:PrivacyNoticeId          100                          ;          (:privacy-notice privacy-notice)
     :PrivacyNoticeConsentTime (b-time/to-unix now)}))