(ns bass4.password.set-responses
  (:require [clojure.string :as str]
            [ring.util.http-response :as http-response]
            [bass4.api-coercion :refer [defapi]]
            [bass4.password.set-services :as set-pw-service]
            [bass4.layout :as layout]
            [bass4.services.bass :as bass-service]
            [bass4.db.core :as db]
            [bass4.clients.core :as clients]
            [bass4.passwords :as passwords]
            [bass4.api-coercion :as api]
            [bass4.services.user :as user-service]
            [bass4.external-messages.email-sender :as email]
            [bass4.external-messages.sms-sender :as sms]
            [bass4.external-messages.sms-queue :as sms-queue]
            [bass4.external-messages.email-queue :as email-queue]))


(defn link!
  [db user-id]
  (let [url (-> (clients/client-scheme+host db)
                (str/replace #"/$" ""))
        uid (set-pw-service/create-uid! db user-id)]
    (str url "/p/" uid)))

(defn link-length
  [db]
  (let [url (-> (clients/client-scheme+host db)
                (str/replace #"/$" ""))]
    (+ set-pw-service/uid-length (count url) (count "/p/"))))

(defapi set-pw-page
  [uid :- [[api/str? 0 100]]]
  (if (set-pw-service/valid? db/*db* uid)
    (layout/render "set-password.html"
                   {:password-regex passwords/password-regex
                    :in-session?    false})
    (assoc
      (layout/render "set-password-invalid-uid.html"
                     {:email       (:email (bass-service/db-contact-info))
                      :in-session? false})
      :status 404)))

(defapi handle-pw
  [uid :- [[api/str? 0 100]] password :- [[api/str? 0 100]]]
  (if (passwords/password-valid? password)
    (if (set-pw-service/set-password! db/*db* uid password)
      (layout/text-response "OK")
      (http-response/bad-request))
    (http-response/bad-request)))

(defapi send-link-page
  [user-id :- api/->int]
  (if-let [user (user-service/get-user user-id)]
    (layout/render "admin/send-pwd-link.html"
                   {:sms-number       (not-empty (:sms-number user))
                    :email            (not-empty (:email user))
                    :sms-max-length   150
                    :email-max-length 1000
                    :link-length      (link-length db/*db*)})
    (http-response/not-found "No such user")))

(defn sms-or-email?
  [s]
  (or (= "sms" s) (= "email" s)))

(defapi handle-send-link
  [request
   user-id :- api/->int
   type :- sms-or-email?
   message :- [[api/str? 0 1000]]
   subject :- [:? [api/str? 0 100]]]
  (if-let [user (user-service/get-user user-id)]
    (cond
      (not (str/includes? message "{LINK}"))
      (http-response/bad-request "{LINK} missing")

      (and (= "sms" type)
           (< 150 (+ (count message) (link-length db/*db*) (- (count "{LINK}")))))
      (http-response/bad-request "SMS too long")

      (and (= "sms" type)
           (not (sms/is-sms-number? (:sms-number user))))
      (http-response/bad-request "User has no valid SMS number")

      (and (= "email" type)
           (str/blank? subject))
      (http-response/bad-request "Subject missing")

      (and (= "email")
           (not (email/is-email? (:email user))))
      (http-response/bad-request "User has no valid email address")

      :else
      (let [link      (link! db/*db* user-id)
            message   (str/replace message "{LINK}" link)
            sender-id (get-in request [:session :user-id])
            redact    (subs (re-find #"/[^/]+$" link) 1)]
        (if (= "sms" type)
          (sms-queue/queue-1! db/*db* user-id (:sms-number user) message redact sender-id)
          (email-queue/queue-1! db/*db* user-id (:email user) subject message redact sender-id))
        (http-response/ok "ok")))
    (http-response/not-found "No such user")))