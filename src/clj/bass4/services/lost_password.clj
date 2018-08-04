(ns bass4.services.lost-password
  (:require [bass4.db.core :as db]
            [bass4.services.bass :as bass-service]
            [clj-time.format :as f]
            [bass4.services.bass :as bass]
            [clj-time.core :as t]))

(defn create-flag!
  [user]
  (let [date-str (-> (f/formatter "yyyy-MM-dd HH:mm" (bass/time-zone))
                     (f/unparse (t/now)))]
    (bass-service/create-flag!
      (:user-id user)
      (str "User reported lost password on " date-str)
      "questionmark.png")))
