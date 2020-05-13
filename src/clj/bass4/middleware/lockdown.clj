(ns bass4.middleware.lockdown
  (:require [ring.util.http-response :as http-response]
            [bass4.external-messages.sms-sender :as sms]
            [bass4.external-messages.email-sender :as email]
            [bass4.now :as now]
            [bass4.external-messages.sms-counter :as sms-counter]
            [bass4.db.core :as db]
            [bass4.config :as config]
            [clj-time.core :as t]
            [clojure.tools.logging :as log]
            [clojure.string :as string]))

(def ^:dynamic locked-down? (atom false))

(defn lock-down!
  [message]
  (reset! locked-down? true)
  (log/error (str "BASS LOCKED DOWN: " message))
  (sms/async-sms! db/*db* (config/env :error-sms) (str "BASS LOCKED DOWN: " message))
  (email/async-email! db/*db* (config/env :error-email) "BASS LOCKED DOWN" message))

(defn response
  []
  (-> (http-response/service-unavailable (str "We are experiencing problems. "
                                              "We are working on the problem "
                                              "and hope that we will be up again soon!"))
      (http-response/content-type "text/plain; charset=utf-8")))

(defn lockdown-mw
  [handler request]
  (if @locked-down?
    (if (when-let [path (:uri request)]
          (string/starts-with? path "/embedded"))
      (handler request)
      (response))
    (handler request)))

;; --------------------------
;;  SMS-COUNTER SURVEILLANCE
;; --------------------------

(def ^:dynamic last-send (atom nil))
(def send-interval 2)
(def too-many 10000)

(defn sms-lockdown-mw
  [handler request]
  (let [sms-count (sms-counter/count)]
    (cond
      (> (/ too-many 2) sms-count)
      (handler request)

      (<= too-many sms-count)
      (do
        (lock-down! (str sms-count " sms have been sent in 24 hrs!"))
        (response))

      (<= (/ too-many 2) sms-count)
      (if (or (nil? @last-send)
              (t/after? (now/now) (t/plus @last-send (t/hours send-interval))))
        (let [message (str sms-count " sms have been sent in 24 hrs!")]
          (sms/async-sms! db/*db* (config/env :error-sms) message)
          (email/async-email! db/*db* (config/env :error-email) message message)
          (reset! last-send (now/now))
          (handler request))
        (handler request))

      :else
      (throw (Exception. (str "We should not be here, sms-count: " sms-count))))))