(ns bass4.password.lost-services
  (:require [bass4.db.core :as db]
            [clj-time.format :as f]
            [clj-time.core :as t]
            [bass4.utils :as utils]
            [bass4.now :as now]
            [bass4.db.orm-classes :as orm]
            [bass4.clients.core :as clients]
            [bass4.passwords :as passwords]))

(defn ^:dynamic lost-password-method []
  (let [method (->
                 (db/get-lost-password-method)
                 first
                 val
                 keyword)]
    (if (#{:report :request-email} method)
      method
      :report)))

(def uid-time-limit
  5400)

(defn create-flag!
  [user]
  (let [date-str (-> (f/formatter "yyyy-MM-dd HH:mm" (t/time-zone-for-id (clients/client-setting [:timezone])))
                     (f/unparse (now/now)))]
    (orm/create-flag!
      (:user-id user)
      "lost-password"
      (str "User reported lost password on " date-str)
      {"CustomIcon" "questionmark.png"})))

(defn get-user-by-username-or-email
  [username-or-email]
  (db/get-user-by-username-or-email {:username-or-email username-or-email}))

(defn create-request-uid!
  [user]
  (let [uid (str (passwords/letters-digits 13 passwords/url-safe-chars) "-" (:user-id user))]
    (db/set-lost-password-request-uid! {:user-id (:user-id user) :uid uid :now (utils/current-time)})
    uid))

(defn get-user-by-request-uid
  [uid]
  (when-let [user (db/get-user-by-lost-password-request-uid! {:uid uid :now (utils/current-time) :time-limit uid-time-limit})]
    (db/reset-lost-password-request-uid! {:user-id (:user-id user)})
    user))
