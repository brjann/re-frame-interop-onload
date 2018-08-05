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
  (when-let [user (user-service/get-user-by-username username)]
    (when (mailer/is-email? (:email user))
      (future
        (let [uid  (lpw-service/create-request-uid user)
              url  (str (h-utils/get-host-address request) "/lost-password/request/uid/" uid)
              mail (i18n/tr [:lost-password/request-email] [url (:email (bass-service/db-contact-info))])]
          (mailer/mail! (:email user) "Request new password" mail)))))
  (http-response/found "/lost-password/request/sent"))

(def-api request-sent
  []
  (layout/render "lost-password-request-sent.html"
                 {:email (:email (bass-service/db-contact-info))}))