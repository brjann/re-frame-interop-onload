(ns bass4.password.set-responses
  (:require [bass4.api-coercion :refer [defapi]]
            [bass4.password.set-services :as set-pw-service]
            [bass4.layout :as layout]
            [bass4.services.bass :as bass-service]
            [ring.util.http-response :as http-response]
            [bass4.password.lost-services :as lpw-service]
            [bass4.i18n :as i18n]
            [bass4.http-utils :as h-utils]
            [bass4.db.core :as db]
            [bass4.clients.core :as clients]
            [clojure.string :as str]
            [bass4.passwords :as passwords]
            [bass4.api-coercion :as api]))


;; -------------------
;;    LOST-PASSWORD
;; -------------------

(defn link
  [db user-id]
  (let [url (-> (clients/client-scheme+host db)
                (str/replace #"/$" ""))
        uid (set-pw-service/create-uid! db user-id)]
    (str url "/p/" uid)))

(defapi set-pw-page
  [uid :- [[api/str? 13 13]]]
  (if (set-pw-service/valid? db/*db* uid)
    (layout/render "set-password.html"
                   {:password-regex passwords/password-regex
                    :in-session?    false})
    (layout/render "set-password-invalid-uid.html"
                   {:email       (:email (bass-service/db-contact-info))
                    :in-session? false})))

(defapi handle-pw
  [uid :- [[api/str? 13 13]] password :- [[api/str? 8 20]]]
  (if (passwords/password-valid? password)
    (if (set-pw-service/set-password! db/*db* uid password)
      (layout/text-response "OK")
      (http-response/not-found "No such user"))
    (http-response/bad-request)))