(ns bass4.services.error-report
  (:require [bass4.db.core :as db]
            [bass4.services.bass :as bass-service]))

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