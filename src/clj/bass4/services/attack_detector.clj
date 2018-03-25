(ns bass4.services.attack-detector
  (:require [bass4.mailer :refer [mail!]]
            [bass4.db.core :as db]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [bass4.config :refer [env]]
            [bass4.utils :refer [filter-map nil-zero?]]
            [bass4.http-utils :as h-utils]
            [bass4.bass-locals :as locals]
            [clj-time.coerce :as tc]
            [bass4.time :as b-time]
            [bass4.request-state :as request-state]
            [bass4.layout :as layout]))

(def ^:const const-fails-until-ip-block 10)
(def ^:const const-block-delay 10)

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
    (if (< const-fails-until-ip-block (count failed-from-ip))
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

(def allowed-request-time (atom {}))

#_(defn delay-time
    [request]
    (let [ip-address (h-utils/get-ip request)]
      (when (ip-blocked? ip-address)
        (let [now                  (t/now)
              request-allowed-time (or (get @allowed-request-time ip-address) now)]
          (swap! blocked-last-request #(assoc % ip-address now))
          (let [seconds-since-request (t/in-seconds (t/interval last-request-time now))]
            (log/debug seconds-since-request)
            (when (>= const-block-delay seconds-since-request)
              (- const-block-delay seconds-since-request)))))))

(def blocked-last-request (atom {}))

(defn get-last-request-time
  [ip-address now]
  (if-let [res (get @blocked-last-request ip-address)]
    res
    (do
      (swap! blocked-last-request #(assoc % ip-address now))
      now)))

(defn delay-time
  [request]
  (let [ip-address (h-utils/get-ip request)]
    (when (ip-blocked? ip-address)
      (let [now               (t/now)
            last-request-time (get-last-request-time ip-address now)]
        (let [delay (let [seconds-since-request (t/in-seconds (t/interval last-request-time now))]
                      (log/debug "Seconds since last request" seconds-since-request)
                      (when (>= const-block-delay seconds-since-request)
                        (- const-block-delay seconds-since-request)))]
          (log/debug "Delay" delay)
          (when (not delay)
            (swap! blocked-last-request #(assoc % ip-address now)))
          delay)))))

(defn- route-success-fn
  [request]
  (when (= (:request-method request) :post)
    (let [attack-routes [{:route   #"/login"
                          :success (fn [in out]
                                     (and (:identity out)
                                          (not= (:identity in) (:identity out))))}
                         {:route   #"/double-auth"
                          :success (fn [in out]
                                     (and (nil-zero? (:double-authed in))
                                          (= 1 (:double-authed out))))}
                         {:route   #"/re-auth"
                          :success (fn [in out]
                                     (and (:auth-re-auth in)
                                          (nil? (:auth-re-auth out))))}
                         {:route   #"/re-auth-ajax"
                          :success (fn [in out]
                                     (and (:auth-re-auth in)
                                          (nil? (:auth-re-auth out))))}]]
      (->> attack-routes
           (filter #(re-matches (:route %) (:uri request)))
           (first)
           (:success)))))

(defn attack-detector-mw
  [handler request]
  (if-let [success-fn (route-success-fn request)]
    (let [response (handler request)]
      (log/debug "Route " (:uri request) " matches")
      #_(log/debug (:session request))
      #_(log/debug (:session response))
      (cond
        (= 422 (:status response))
        (do
          (log/debug "Login failed")
          (register-failed-login! :login request)
          #_(delay-if-blocked! request)
          (if-let [delay (delay-time request)]
            (do
              (log/debug "Sleep for " delay " seconds")
              (layout/error-429 delay))
            response
            #_(Thread/sleep (* 1000 delay))))

        (success-fn (:session request) (:session response))
        (do
          (log/debug "Login successful")
          (register-successful-login! request)
          response)

        :else
        (do
          (log/debug "Something else")
          response)))
    (handler request)))

;;
;; TODO: Test password fail timing
;; TODO: Add to all login/password sites
;; TODO: Block all IPs if 100 failures
;; TODO: Block user after too many failed login attempts
;;

