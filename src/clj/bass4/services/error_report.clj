(ns bass4.services.error-report
  (:require [bass4.db.core :as db]
            [buddy.hashers :as hashers]
            [bass4.config :as config]
            [bass4.time :as b-time]
            [bass4.services.bass :as bass-service]))

(defn close-no-consent-flag!
  [user-id now]
  (let [close-fn (fn [flag-id]
                   (let [comment-id (:objectid (db/create-bass-object! {:class-name    "cComment"
                                                                        :parent-id     flag-id
                                                                        :property-name "Comments"}))]
                     (db/update-object-properties! {:table-name "c_comment"
                                                    :object-id  comment-id
                                                    :updates    {:Text "User consented"}})
                     (db/update-object-properties! {:table-name "c_flag"
                                                    :object-id  flag-id
                                                    :updates    {:ClosedAt (b-time/to-unix now)}})))
        flag-ids (db/get-no-consent-flags {:user-id user-id})]
    (mapv #(close-fn (:flag-id %)) flag-ids)))

(defn create-error-report-flag!
  [user-id error-description]
  (let [flag-id (bass-service/create-flag!
                  user-id
                  "error-report"
                  "User has reported an error"
                  "alert.gif")]
    (let [comment-id (:objectid (db/create-bass-object! {:class-name    "cComment"
                                                         :parent-id     flag-id
                                                         :property-name "Comments"}))]
      (db/update-object-properties! {:table-name "c_comment"
                                     :object-id  comment-id
                                     :updates    {:Text (str "ERROR REPORT\n" error-description)}}))))