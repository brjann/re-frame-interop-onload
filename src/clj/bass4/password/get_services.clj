(ns bass4.password.get-services
  (:require [bass4.passwords :as passwords]
            [clojure.java.jdbc :as jdbc]
            [bass4.db.core :as db]
            [bass4.now :as now]
            [clj-time.core :as t]))

(defn gen-uid
  []
  (passwords/letters-digits 13 passwords/url-safe-chars))

(defn create-uid!
  [password]
  (let [uid         (gen-uid)
        valid-until (t/plus (now/now) (t/days 2))]
    (jdbc/execute! db/db-common ["INSERT INTO passwords (`uid`, `password`, `valid-until`) VALUES (?,?,?)"
                                 uid, password valid-until])
    uid))