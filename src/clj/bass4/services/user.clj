(ns bass4.services.user
  (:require [bass4.db.core :as db]))

(defn get-user [user-id]
  (when user-id
    (if-let [user (db/get-user {:id user-id})]
      (assoc user :user-id (:objectid user)))))