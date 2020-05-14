(ns bass4.password.set-services
  (:require [bass4.passwords :as passwords]
            [clojure.java.jdbc :as jdbc]
            [bass4.now :as now]
            [clj-time.core :as t]
            [clojure.string :as str]
            [bass4.clients.core :as clients]))

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

(defn link
  [db user-id]
  (let [url (-> (clients/client-scheme+host db)
                (str/replace #"/$" ""))
        uid (create-uid! db user-id)]
    (str url "/p/" uid)))

(defn valid?
  [db uid]
  (some? (seq (jdbc/query db ["SELECT `uid` FROM password_uid WHERE `uid` = ? AND `valid-until` > ?"
                              uid (now/now)]))))