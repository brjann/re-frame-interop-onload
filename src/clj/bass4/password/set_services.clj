(ns bass4.password.set-services
  (:require [bass4.passwords :as passwords]
            [clojure.java.jdbc :as jdbc]
            [bass4.now :as now]
            [clj-time.core :as t]
            [clojure.string :as str]
            [bass4.clients.core :as clients]
            [bass4.services.user :as user-service]))

(defn gen-uid
  []
  (passwords/letters-digits 13 passwords/url-safe-chars))

(defn create-uid!
  [db user-id]
  (let [uid         (gen-uid)
        valid-until (t/plus (now/now) (t/days 2))]
    (jdbc/execute! db ["INSERT INTO password_uid (`uid`, `user-id`, `valid-until`) VALUES (?,?,?)"
                       uid user-id valid-until])
    uid))

(defn user-id
  [db uid]
  (-> (jdbc/query db ["SELECT `user-id` FROM password_uid WHERE `uid` = ? AND `valid-until` > ?"
                      uid (now/now)])
      (first)
      :user-id))

(defn valid?
  [db uid]
  (some? (user-id db uid)))

(defn set-password!
  [db uid password]
  (when-let [user-id (user-id db uid)]
    (jdbc/execute! db ["DELETE FROM password_uid WHERE `uid` = ?"
                       uid])
    (jdbc/execute! db ["UPDATE c_participant SET `Password` = ? WHERE `ObjectId` = ?"
                       (user-service/password-hasher password) user-id])
    true))