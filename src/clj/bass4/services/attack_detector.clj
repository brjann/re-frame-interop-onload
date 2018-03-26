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
(def ^:const const-fails-until-global-block 100)            ;; Should be factor of 10 for test to work
(def ^:const const-global-block-ip-count 3)
(def ^:const const-ip-block-delay 10)
(def ^:const const-global-block-delay 2)
(def ^:const const-attack-interval 900)

(def blocked-ips (atom {}))
(def global-block (atom nil))

(defn get-failed-logins
  [now]
  (db/get-failed-logins {:time now :attack-interval const-attack-interval}))

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
    (if (<= const-fails-until-ip-block (count failed-from-ip))
      (swap! blocked-ips #(assoc % ip-address now))
      (swap! blocked-ips #(dissoc % ip-address now)))
    (if (and (<= const-fails-until-global-block (count failed-logins))
             (<= const-global-block-ip-count (count (group-by :ip-address failed-logins))))
      (swap! global-block (constantly now))
      ;; TODO: Can global-block be used instead of last-request-global?
      (swap! global-block (constantly nil)))))

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

(def blocked-last-request (atom {}))

(defn get-last-request-time
  [ip-address now]
  (let [res (get @blocked-last-request ip-address)]
    (if (or (nil? res) (t/before? now res))
      (do
        (swap! blocked-last-request #(assoc % ip-address now))
        now)
      res)))

(defn delay-ip!
  [request]
  (let [ip-address (h-utils/get-ip request)]
    (when (ip-blocked? ip-address)
      (let [now               (t/now)
            last-request-time (get-last-request-time ip-address now)]
        (let [delay (let [seconds-since-request (t/in-seconds (t/interval last-request-time now))]
                      (when (> const-ip-block-delay seconds-since-request)
                        (- const-ip-block-delay seconds-since-request)))]
          (when (not delay)
            (swap! blocked-last-request #(assoc % ip-address now)))
          delay)))))

(def global-last-request (atom nil))

(defn get-last-request-time-global
  [now]
  (let [res @global-last-request]
    (if (or (nil? res) (t/before? now res))
      (do
        (swap! global-last-request (constantly now))
        now)
      res)))

(defn delay-global!
  []
  (when @global-block
    (let [now               (t/now)
          last-request-time (get-last-request-time-global now)]
      (let [delay (let [seconds-since-request (t/in-seconds (t/interval last-request-time now))]
                    (when (> const-global-block-delay seconds-since-request)
                      (- const-global-block-delay seconds-since-request)))]
        (when (not delay)
          (swap! global-last-request (constantly nil)))
        delay))))

(defn get-delay-time
  [request]
  (or (delay-ip! request) (delay-global!)))

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
                                          (nil? (:auth-re-auth out))))}
                         {:route   #"/registration/[0-9]+/captcha"
                          :success (fn [in out]
                                     (and (not (:captcha-ok in))
                                          (:captcha-ok out)))}]]
      (->> attack-routes
           (filter #(re-matches (:route %) (:uri request)))
           (first)
           (:success)))))

(defn attack-detector-mw
  [handler request]
  (if-let [success-fn (route-success-fn request)]
    (if-let [delay (get-delay-time request)]
      (layout/error-429 delay)
      (let [response (handler request)]
        (cond
          (= 422 (:status response))
          (do
            (register-failed-login! :login request)
            (delay-ip! request)
            (delay-global!)
            response)

          (success-fn (:session request) (:session response))
          (do
            (register-successful-login! request)
            response)

          :else
          response)))
    (handler request)))

;;
;; TODO: Block user after too many failed login attempts
;;

