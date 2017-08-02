(ns bass4.services.user
  (:require [bass4.db.core :as db]))

(defn get-user [user-id]
  (when user-id
    (if-let [user (db/get-user-by-user-id {:user-id user-id})]
      (assoc user :user-id (:objectid user)))))


#_(defn get-user [user-id]
  (when user-id
    (db/get-user-by-user-id {:user-id user-id})))