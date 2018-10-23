(ns bass4.services.auth
  (:require [bass4.db.core :as db]
            [clojure.tools.logging :as log]
            [buddy.hashers :as hashers]
            [bass4.config :refer [env]]
            [bass4.services.user :as user-service]
            [bass4.passwords :as passwords]))

(defn double-auth-code []
  (passwords/letters-digits 3))

(defn double-auth-required? [user-id]
  (if-let [settings (db/get-double-auth-settings {:user-id user-id})]
    (let [{:keys [sms? email? user-skip? allow-skip?]} settings]
      (cond
        (and (not sms?) (not email?)) false
        (and allow-skip? user-skip?) false
        :else settings))
    false))

(defn- authenticate-user
  [user password]
  (let [user (user-service/upgrade-password! user)]
    (when-not (empty? (:password user))
      (try (when (hashers/check password (:password user))
             user)
           (catch Exception _)))))

(defn authenticate-by-user-id [user-id password]
  (when-let [user (db/get-user-by-user-id {:user-id user-id})]
    (authenticate-user user password)))

(defn authenticate-by-username [username password]
  (when-let [user (db/get-user-by-username {:username username})]
    (authenticate-user user password)))

(defn register-user-login! [user-id]
  (db/update-last-login! {:user-id user-id})
  (db/update-login-count! {:user-id user-id}))