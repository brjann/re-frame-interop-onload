(ns bass4.external-messages.sms-status
  (:require [compojure.core :refer [defroutes context GET POST ANY routes]]
            [bass4.api-coercion :as api :refer [defapi]]
            [clj-time.format :as tf]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [bass4.db.core :as db]
            [bass4.layout :as layout]
            [bass4.services.bass :as bass]
            [clj-time.core :as t]
            [bass4.clients.core :as clients]))

(defn db-update-status!
  [db provider-id status time]
  (jdbc/execute! db [(str "UPDATE external_message_sms SET `status` = ?, `status-time` = ?"
                          "WHERE `provider-id` = ?")
                     status
                     time
                     provider-id]))

(defn parse-datetime
  [datetime tz-id]
  (tf/parse (tf/formatter (:mysql tf/formatters)
                          (t/time-zone-for-id tz-id)) datetime))

(defapi update-status
  [ref :- api/->int state :- [[api/str? 0 20]] datetime :- [[api/str? 0 20]]]
  (try
    (let [time   (parse-datetime datetime "Europe/Stockholm")
          status (str/lower-case state)]
      (db-update-status! db/*db* ref status time)
      (layout/text-response "ok"))
    (catch Exception _
      (-> (layout/text-response "Bad request")
          (assoc :status 400)))))

(defn status-url
  [db]
  (let [db-url (-> (clients/client-host db)
                   (str/replace #"/$" ""))]
    (str db-url "/sms-status")))

(defroutes route
  (POST "/sms-status" [nr ref state text datetime]
    (update-status ref state datetime)))