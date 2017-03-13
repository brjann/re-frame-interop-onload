(ns bass4.services.auth
  (:require [bass4.db.core :as db]
            [ring.util.http-response :as response]
            #_[buddy.hashers :as hashers]
            [clojure.tools.logging :as log]))

#_(defn authenticate [id password]
    (when-let [user (db/get-user {:id id})]
      (when (hashers/check password (:password user))
        id)))

#_(defn authenticate [id password]
  (when-let [user (db/get-user {:id id})]
    (when (= (:password user) password)
      id)))

(defn authenticate-by-username [username password]
  (when-let [user (db/get-user-by-username {:username username})]
    (when (= (:password user) password)
      (:objectid user))))

(defn login! [{:keys [session]} {:keys [username password]}]
  (if-let [id (authenticate-by-username username password)]
    (-> (response/found "/about")
        (assoc :session (assoc session :identity id)))
    (response/unauthorized {:result :unauthorized
                            :message "login failure"})))

(defn logout! []
  (-> (response/found "/login")
      (assoc :session nil)))