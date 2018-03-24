(ns bass4.services.attack-detector
  (:require [bass4.mailer :refer [mail!]]
            [bass4.db.core :as db]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [bass4.config :refer [env]]
            [bass4.utils :refer [filter-map]]
            [bass4.http-utils :as h-utils]
            [bass4.bass-locals :as locals]
            [clj-time.coerce :as tc]
            [bass4.time :as b-time]
            [bass4.request-state :as request-state]))

(def blocked-ips (atom {}))

(defn get-failed-logins
  [now]
  (db/get-failed-logins {:time now}))

(defn logins-by-ip
  [logins]
  (->> logins
       (filter :ip-address)
       (group-by :ip-address)))

(defn update-blocked-ips!
  [ip-address now]
  (let [failed-logins  (get-failed-logins now)
        failed-from-ip (-> failed-logins
                           (logins-by-ip)
                           (get ip-address))]
    (if (<= 10 (count failed-from-ip))
      (swap! blocked-ips #(assoc % ip-address now))
      (swap! blocked-ips #(dissoc % ip-address now)))))

(defn save-failed-login!
  [type db ip-address info now]
  (let [record (merge {:type       type
                       :db         db
                       :time       (b-time/to-unix now)
                       :ip-address ip-address
                       :user-id    ""
                       :username   ""}
                      info)]
    (db/save-failed-login! record)))

(defn register-failed-login!
  ([type request] (register-failed-login! type request {}))
  ([type request info]
   (let [now        (t/now)
         ip-address (h-utils/get-ip request)]
     (save-failed-login!
       (name type)
       (:db-name locals/*local-config*)
       ip-address
       info
       now)
     (update-blocked-ips! ip-address now))))

(defn ip-successful-login!
  [ip-address]
  (swap! blocked-ips #(dissoc % ip-address)))

(defn register-successful-login!
  [request]
  (ip-successful-login! (h-utils/get-ip request)))

(defn ip-blocked?
  [ip-address]
  (contains? @blocked-ips ip-address))

(defn sleep!
  []
  (Thread/sleep 5000))

(defn delay-if-blocked!
  [request]
  (when (ip-blocked? (h-utils/get-ip request))
    (sleep!)))

;;
;; TODO: Test password fail timing
;; TODO: Add to all login/password sites
;; TODO: Block all IPs if 100 failures
;; TODO: Block user after too many failed login attempts
;;