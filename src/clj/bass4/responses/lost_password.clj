(ns bass4.responses.lost-password
  (:require [bass4.api-coercion :as api :refer [def-api]]
            [bass4.layout :as layout]
            [bass4.services.bass :as bass-service]
            [bass4.services.user :as user-service]
            [clojure.tools.logging :as log]
            [ring.util.http-response :as http-response]
            [bass4.services.lost-password :as lpw-service]))


;; -------------------
;;    NO ACTIVITIES
;; -------------------

(def-api lost-password-page []
  (let [email (:email (bass-service/db-contact-info))]
    (layout/render
      "lost-password.html"
      {:email email})))

(def-api handle-username
  [username :- api/str+!]
  (when-let [user (user-service/get-user-by-username username)]
    (lpw-service/create-flag! user))
  (http-response/ok))