(ns bass4.services.user
  (:require [bass4.db.core :as db]))

(defn get-user [user-id]
  (db/get-user {:id user-id}))