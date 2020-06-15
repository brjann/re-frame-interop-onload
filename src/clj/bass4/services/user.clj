(ns bass4.services.user
  (:require [bass4.db.core :as db]
            [buddy.hashers :as hashers]
            [bass4.config :as config]
            [clojure.string :as str]
            [bass4.utils :as utils]
            [bass4.db.orm-classes :as orm]
            [bass4.now :as now]
            [clojure.java.jdbc :as jdbc]))

(defn get-user
  [user-id]
  (when user-id
    (if-let [user (db/get-user-by-user-id {:user-id user-id})]
      (assoc user :user-id (:objectid user)))))

(defn get-user-by-username [username]
  (db/get-user-by-username {:username username}))

(defn get-1-user-by-pid-number [pid-number]
  (let [users (db/get-user-by-pid-number {:pid-number pid-number})]
    (when (= 1 (count users))
      (first users))))

(defn get-users-by-participant-id
  [participant-id]
  (when-let [users (db/get-user-by-participant-id {:participant-id participant-id})]
    (mapv #(assoc % :user-id (:objectid %)) users)))

(defn user-group-id
  [db user-id]
  (:group-id (db/get-user-group db {:user-id user-id})))

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

(def ^:dynamic *log-user-changes* true)

(defn update-user-properties!
  ([user-id properties] (update-user-properties! user-id properties ""))
  ([user-id properties cause]
   (let [properties (into {} (map (fn [[k v]] [(if (keyword? k) k (keyword k)) v]) properties))
         properties (merge properties
                           (->> (select-keys properties [:FirstName :LastName :Email :SMSNumber :Personnummer])
                                (utils/map-map utils/remove-html)))
         properties (into {} (map (fn [[k v]] [((comp keyword str/lower-case name) k) v]) properties))
         properties (if (:password properties)
                      (let [password-hash (-> (:password properties)
                                              (str)
                                              (password-hasher))]
                        (assoc properties :password password-hash))
                      properties)]
     (when *log-user-changes*
       (let [rows (map (fn [[k v]]
                         [user-id "property" (name k)
                          (if (= :password k) "(SECRET)" v)
                          0 (utils/to-unix (now/now)) cause])
                       properties)]
         (jdbc/insert-multi! db/*db* "log_participant_changes"
                             ["ParticipantId" "Type" "PropertyName" "NewValue" "ChangerId" "Time" "Comment"]
                             rows)))
     (db/update-user-properties! {:user-id user-id :updates (merge properties
                                                                   (when (contains? properties :password)
                                                                     {(keyword "LastPasswordChange") (utils/to-unix (now/now))}))}))))

(defn create-user!
  ([project-id] (create-user! project-id nil))
  ([project-id properties] (create-user! project-id properties ""))
  ([project-id properties cause]
   (let [collection-id (:collection-id (db/get-project-participant-collection {:project-id project-id}))
         user-id       (:objectid (orm/create-bass-object-map! {:class-name    "cParticipant"
                                                                :parent-id     collection-id
                                                                :property-name "Participants"}))]
     (when *log-user-changes*
       (jdbc/execute! db/*db* [(str "INSERT INTO log_participant_changes (ParticipantId, Type, ChangerId, Time, Comment) "
                                    "VALUES (?, ?, ?, ?, ?)")
                               user-id "create" 0 (utils/to-unix (now/now)) cause]))
     (when properties
       (update-user-properties! user-id properties cause))
     user-id)))

(defn set-user-privacy-consent!
  [user-id privacy-notice-id now]
  (update-user-properties!
    user-id
    {"PrivacyNoticeId"          privacy-notice-id
     "PrivacyNoticeConsentTime" (utils/to-unix now)}
    "privacy consent"))

;; ---------------------
;;  NO CONSENT FLAGGING
;; ---------------------

;; TODO: Move to privacy service
(defn close-no-consent-flag!
  [user-id now]
  (let [close-fn (fn [flag-id]
                   (let [comment-id (:objectid (orm/create-bass-object-map! {:class-name    "cComment"
                                                                             :parent-id     flag-id
                                                                             :property-name "Comments"}))]
                     (db/update-object-properties! {:table-name "c_comment"
                                                    :object-id  comment-id
                                                    :updates    {:Text "User consented"}})
                     (db/update-object-properties! {:table-name "c_flag"
                                                    :object-id  flag-id
                                                    :updates    {:ClosedAt (utils/to-unix now)}})))
        flag-ids (db/get-no-consent-flags {:user-id user-id})]
    (mapv #(close-fn (:flag-id %)) flag-ids)))

(defn create-no-consent-flag!
  [user-id]
  (orm/create-flag!
    user-id
    "no-consent"
    (str "User did not consent to Privacy Notice")
    {"CustomIcon" "flag-high"}))