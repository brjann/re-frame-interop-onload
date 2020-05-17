(ns bass4.password.set-services
  (:require [bass4.passwords :as passwords]
            [clojure.java.jdbc :as jdbc]
            [bass4.now :as now]
            [clj-time.core :as t]
            [bass4.services.user :as user-service]
            [bass4.db.core :as db]))

(def uid-length 13)

(defn gen-uid
  []
  (passwords/letters-digits uid-length passwords/url-safe-chars))

(defn create-uid!
  [db user-id]
  (let [uid         (gen-uid)
        valid-until (t/plus (now/now) (t/days 2))]
    (jdbc/execute! db [(str "INSERT INTO password_uid (`uid`, `user-id`, `valid-until`) VALUES (?,?,?) "
                            "ON DUPLICATE KEY UPDATE `uid` = VALUES(`uid`), `valid-until` = VALUES(`valid-until`)")
                       uid user-id valid-until])
    uid))

(defn user-valid-until
  [db user-id]
  (-> (jdbc/query db ["SELECT `valid-until` FROM password_uid WHERE `user-id` = ? AND `valid-until` > ?"
                      user-id (now/now)])
      (first)
      (vals)
      (first)))

(defn delete-uid!
  [db uid]
  (jdbc/execute! db ["DELETE FROM password_uid WHERE uid = ?" uid]))

(defn uid->user-id
  [db uid]
  (-> (jdbc/query db ["SELECT `user-id` FROM password_uid WHERE `uid` = ? AND `valid-until` > ?"
                      uid (now/now)])
      (first)
      :user-id))

(defn user?
  [db user-id]
  (when user-id
    (-> (jdbc/query db ["SELECT `ObjectId` FROM c_participant WHERE ObjectId = ?" user-id])
        (seq)
        (some?))))

(defn valid?
  [db uid]
  (if-not (user? db (uid->user-id db uid))
    (do
      (delete-uid! db uid)
      false)
    true))

(defn set-password!
  [db uid password]
  (when-let [user-id (uid->user-id db uid)]
    (jdbc/execute! db ["DELETE FROM password_uid WHERE `uid` = ?"
                       uid])
    (-> (jdbc/execute! db ["UPDATE c_participant SET `Password` = ? WHERE `ObjectId` = ?"
                           (user-service/password-hasher password) user-id])
        (first)
        (= 1))))