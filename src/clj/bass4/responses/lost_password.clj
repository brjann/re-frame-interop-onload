(ns bass4.responses.lost-password
  (:require [bass4.api-coercion :as api :refer [def-api]]
            [bass4.layout :as layout]
            [bass4.services.bass :as bass-service]
            [bass4.services.user :as user-service]
            [clojure.tools.logging :as log]
            [ring.util.http-response :as http-response]
            [bass4.services.lost-password :as lpw-service]
            [bass4.email :as mailer]
            [bass4.i18n :as i18n]
            [bass4.http-utils :as h-utils]))


;; -------------------
;;    NO ACTIVITIES
;; -------------------

(def-api request-page []
  (let [email (:email (bass-service/db-contact-info))]
    (layout/render
      "lpw-request-email.html"
      {:email email})))

(def-api handle-request
  [username :- api/str+! request :- map?]
  (if-let [user (lpw-service/get-user-by-username-or-email username)]
    (if (mailer/is-email? (:email user))
      (let [uid    (lpw-service/create-request-uid! user)
            url    (str (h-utils/get-host-address request) "/lpw-uid/" uid)
            mail   (i18n/tr [:lost-password/request-email-text] [url (:email (bass-service/db-contact-info))])
            header (i18n/tr [:lost-password/request-email-header])]
        (mailer/queue-email! (:email user) header mail))
      ;; If user has no email address - then flag anyway but act as if email was sent.
      (lpw-service/create-flag! user)))
  (http-response/found "/lost-password/request-email/sent"))

(def-api request-sent
  []
  (layout/render "lpw-request-email-sent.html"
                 {:email (:email (bass-service/db-contact-info))}))

(def-api handle-request-uid
  [uid :- api/str+!]
  (if-let [user (lpw-service/get-user-by-request-uid uid)]
    (do (lpw-service/create-flag! user)
        (http-response/found "/lost-password/request-email/received"))
    (http-response/found "/lost-password/request-email/not-found")))

(def-api request-received
  []
  (layout/render "lpw-request-email-received.html"))

(def-api request-not-found
  []
  (layout/render "lpw-request-email-not-found.html"
                 {:email (:email (bass-service/db-contact-info))}))

(def-api report-page
  []
  (layout/render "lpw-report.html"
                 {:email (:email (bass-service/db-contact-info))}))

(def-api handle-report
  [username :- api/str+!]
  (if-let [user (lpw-service/get-user-by-username-or-email username)]
    (lpw-service/create-flag! user))
  (http-response/found "/lost-password/report/received"))

(def-api report-received
  []
  (layout/render "lpw-report-received.html"
                 {:email (:email (bass-service/db-contact-info))}))

;; TODO: Tests
;; - Flow post - sms - request - flag created
;; - Flow post - sms - request after timeout - no flag created
;; - No email / sms - flag created anyway