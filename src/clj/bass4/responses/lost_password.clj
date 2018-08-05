(ns bass4.responses.lost-password
  (:require [bass4.api-coercion :as api :refer [def-api]]
            [bass4.layout :as layout]
            [bass4.services.bass :as bass-service]
            [bass4.services.user :as user-service]
            [clojure.tools.logging :as log]
            [ring.util.http-response :as http-response]
            [bass4.services.lost-password :as lpw-service]
            [bass4.mailer :as mailer]
            [bass4.i18n :as i18n]
            [bass4.http-utils :as h-utils]))


;; -------------------
;;    NO ACTIVITIES
;; -------------------

(def-api lost-password-page []
  (let [email (:email (bass-service/db-contact-info))]
    (layout/render
      "lost-password.html"
      {:email email})))

(def-api handle-request
  [username :- api/str+! request :- map?]
  (if-let [user (lpw-service/get-user-by-username-or-email username)]
    (if (mailer/is-email? (:email user))
      (let [uid    (lpw-service/create-request-uid! user)
            url    (str (h-utils/get-host-address request) "/lpw-uid/" uid)
            mail   (i18n/tr [:lost-password/request-email-text] [url (:email (bass-service/db-contact-info))])
            header (i18n/tr [:lost-password/request-email-header])]
        (future (mailer/mail! (:email user) header mail)))
      ;; TODO: Flag if no email address
      ))
  (http-response/found "/lost-password/request/sent"))

(def-api request-sent
  []
  (layout/render "lost-password-request-sent.html"
                 {:email (:email (bass-service/db-contact-info))}))

(def-api handle-request-uid
  [uid :- api/str+!]
  (if-let [user (lpw-service/get-user-by-request-uid uid)]
    (do (lpw-service/create-flag! user)
        (http-response/found "/lost-password/request/received"))))