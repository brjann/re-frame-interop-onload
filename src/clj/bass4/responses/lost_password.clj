(ns bass4.responses.lost-password
  (:require [bass4.api-coercion :as api :refer [def-api]]
            [bass4.layout :as layout]
            [bass4.services.bass :as bass-service]))


;; -------------------
;;    NO ACTIVITIES
;; -------------------

(def-api lost-password-page []
  (let [email (:email (bass-service/db-contact-info))]
    (layout/render
      "lost-password.html"
      {:email email})))
