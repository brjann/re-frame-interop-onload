(ns bass4.services.lost-password
  (:require [bass4.db.core :as db]
            [bass4.services.bass :as bass-service]
            [clj-time.format :as f]
            [bass4.services.bass :as bass]
            [clj-time.core :as t]
            [clojure.tools.logging :as log])
  (:import (java.util UUID)))

(defn lost-password-method []
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
  (let [date-str (-> (f/formatter "yyyy-MM-dd HH:mm" (bass/time-zone))
                     (f/unparse (t/now)))]
    (bass-service/create-flag!
      (:user-id user)
      (str "User reported lost password on " date-str)
      "questionmark.png")))

(defn get-user-by-username-or-email
  [username-or-email]
  (db/get-user-by-username-or-email {:username-or-email username-or-email}))

(defn create-request-uid!
  [user]
  (let [uid (str (subs (str (UUID/randomUUID)) 0 13) "-" (:user-id user))]
    (db/set-lost-password-request-uid! {:user-id (:user-id user) :uid uid :now (t/now)})
    uid))

(defn get-user-by-request-uid
  [uid]
  (when-let [user (db/get-user-by-lost-password-request-uid! {:uid uid :now (t/now) :time-limit uid-time-limit})]
    (db/reset-lost-password-request-uid! {:user-id (:user-id user)})
    user))
