(ns bass4.services.user
  (:require [bass4.db.core :as db]
            [buddy.hashers :as hashers]
            [bass4.config :as config]
            [bass4.time :as b-time]
            [bass4.services.bass :as bass-service]
            [clojure.string :as str]
            [bass4.utils :as utils]))

(defn get-user
  [user-id]
  (when user-id
    (if-let [user (db/get-user-by-user-id {:user-id user-id})]
      (assoc user :user-id (:objectid user)))))

(defn get-user-by-username [username]
  (db/get-user-by-username {:username username}))

(defn get-users-by-participant-id
  [participant-id]
  (when-let [users (db/get-user-by-participant-id {:participant-id participant-id})]
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
    (db/update-user-properties! {:user-id user-id :updates properties})))

(defn create-user!
  ([project-id] (create-user! project-id nil))
  ([project-id properties]
   (let [collection-id (:collection-id (db/get-project-participant-collection {:project-id project-id}))
         user-id       (:objectid (db/create-bass-object! {:class-name    "cParticipant"
                                                           :parent-id     collection-id
                                                           :property-name "Participants"}))]
     (when properties
       (update-user-properties! user-id properties))
     user-id)))

(defn set-user-privacy-consent!
  [user-id privacy-notice-id now]
  (update-user-properties!
    user-id
    {:PrivacyNoticeId          privacy-notice-id
     :PrivacyNoticeConsentTime (b-time/to-unix now)}))

;; ---------------------
;;  NO CONSENT FLAGGING
;; ---------------------

;; TODO: Move to privacy service
(defn close-no-consent-flag!
  [user-id now]
  (let [close-fn (fn [flag-id]
                   (let [comment-id (:objectid (db/create-bass-object! {:class-name    "cComment"
                                                                        :parent-id     flag-id
                                                                        :property-name "Comments"}))]
                     (db/update-object-properties! {:table-name "c_comment"
                                                    :object-id  comment-id
                                                    :updates    {:Text "User consented"}})
                     (db/update-object-properties! {:table-name "c_flag"
                                                    :object-id  flag-id
                                                    :updates    {:ClosedAt (b-time/to-unix now)}})))
        flag-ids (db/get-no-consent-flags {:user-id user-id})]
    (mapv #(close-fn (:flag-id %)) flag-ids)))

(defn create-no-consent-flag!
  [user-id]
  (bass-service/create-flag!
    user-id
    "no-consent"
    (str "User did not consent to Privacy Notice")
    "flag-high"))