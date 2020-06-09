(ns bass4.services.attack-detector
  (:require [clojure.tools.logging :as log]
            [bass4.db.core :as db]
            [bass4.clients.core :as clients]
            [bass4.config :refer [env]]
            [bass4.utils :refer [filter-map nil-zero?]]
            [bass4.http-utils :as h-utils]
            [bass4.config :as config]
            [bass4.http-errors :as http-errors]
            [bass4.utils :as utils]))

(def ^:const const-fails-until-ip-block 10)
(def ^:const const-fails-until-global-block 100)            ;; Should be factor of 10 for test to work
(def ^:const const-global-block-ip-count 3)
(def ^:const const-ip-block-delay 10)
(def ^:const const-global-block-delay 2)
(def ^:const const-attack-interval 900)

;; ----------------------
;;  MANAGE FAILED LOGINS
;; ----------------------

(def ^:dynamic blocked-ips (atom {}))
(def ^:dynamic global-block (atom nil))

(defn ^:dynamic db-save-failed-login!
  [record]
  (db/save-failed-login! record))

(defn ^:dynamic db-get-failed-logins
  [now attack-interval]
  (db/get-failed-logins {:time now :attack-interval attack-interval}))

(defn logins-by-ip
  [logins]
  (->> logins
       (filter :ip-address)
       (group-by :ip-address)))

(defn update-blocked-ips!
  [ip-address now]
  (let [failed-logins  (db-get-failed-logins now const-attack-interval)
        failed-from-ip (-> failed-logins
                           (logins-by-ip)
                           (get ip-address))]
    (if (<= const-fails-until-ip-block (count failed-from-ip))
      (swap! blocked-ips #(assoc % ip-address now))
      (swap! blocked-ips #(dissoc % ip-address now)))
    (if (and (<= const-fails-until-global-block (count failed-logins))
             (<= const-global-block-ip-count (count (group-by :ip-address failed-logins))))
      (reset! global-block now)
      ;; TODO: Can global-block be used instead of last-request-global?
      (reset! global-block nil))))

(defn save-failed-login!
  [type db ip-address info now]
  (let [record (merge {:type       type
                       :db         db
                       :time       now
                       :ip-address ip-address
                       :user-id    ""
                       :username   ""}
                      info)]
    (db-save-failed-login! record)))

(defn ^:dynamic register-failed-login!
  ([type request] (register-failed-login! type request {}))
  ([type request info]
   (let [now        (utils/current-time)
         ip-address (h-utils/get-client-ip request)]
     (save-failed-login!
       (name type)
       (:db-name clients/*client-config*)
       ip-address
       info
       now)
     (update-blocked-ips! ip-address now))))

(defn ip-successful-login!
  [ip-address]
  (swap! blocked-ips #(dissoc % ip-address)))

(defn register-successful-login!
  [request]
  (ip-successful-login! (h-utils/get-client-ip request)))

;; --------------------
;;    SINGLE IP ATTACK
;; --------------------

(defn ip-blocked?
  [ip-address]
  (contains? @blocked-ips ip-address))

(def ^:dynamic blocked-last-request (atom {}))

(defn get-last-request-time
  [ip-address now]
  (let [res (get @blocked-last-request ip-address)]
    (if (or (nil? res) (< now res))
      (do
        (swap! blocked-last-request #(assoc % ip-address now))
        now)
      res)))

(defn delay-ip!
  [request]
  (let [ip-address (h-utils/get-client-ip request)]
    (when (ip-blocked? ip-address)
      (let [now               (utils/current-time)
            last-request-time (get-last-request-time ip-address now)]
        (let [delay (let [seconds-since-request (- now last-request-time)]
                      (when (> const-ip-block-delay seconds-since-request)
                        (- const-ip-block-delay seconds-since-request)))]
          (when (not delay)
            (swap! blocked-last-request #(assoc % ip-address now)))
          delay)))))

;; --------------------
;;    GLOBAL ATTACK
;; --------------------

(def ^:dynamic global-last-request (atom nil))

(defn get-last-request-time-global
  [now]
  (let [res @global-last-request]
    (if (or (nil? res) (< now res))
      (do
        (reset! global-last-request now)
        now)
      res)))

(defn delay-global!
  []
  (when @global-block
    (let [now               (utils/current-time)
          last-request-time (get-last-request-time-global now)]
      (let [delay (let [seconds-since-request (- now last-request-time)]
                    (when (> const-global-block-delay seconds-since-request)
                      (- const-global-block-delay seconds-since-request)))]
        (when (not delay)
          (reset! global-last-request nil))
        delay))))

;; --------------------
;;    ATTACK VECTORS
;; --------------------

(defn ^:dynamic get-delay-time
  [request]
  (or (delay-ip! request) (delay-global!)))

(defn- match-attack-vector
  [request]
  (let [fail-fn        (fn [response]
                         (= 422 (:status response)))
        delay-response (fn [delay] (http-errors/too-many-requests-429 delay))
        default-config {:method         :post
                        :fail           fail-fn
                        :delay-response delay-response
                        :fail-info      (constantly nil)}
        attack-routes  [{:route     #"/login"
                         :name      "login"
                         :success   (fn [in out]
                                      (and (:user-id out)
                                           (not= (:user-id in) (:user-id out))))
                         :fail-info (fn [request]
                                      {:username (get-in request [:params :username])})}
                        {:route     #"/double-auth"
                         :name      "doubleauth"
                         :success   (fn [in out]
                                      (and (not (:double-authed? in))
                                           (:double-authed? out)))
                         :fail-info (fn [request]
                                      {:user-id (get-in request [:session :user-id])})}
                        {:route     #"/re-auth"
                         :name      "reauth"
                         :success   (fn [in out]
                                      (and (:auth-re-auth? in)
                                           (nil? (:auth-re-auth? out))))
                         :fail-info (fn [request]
                                      {:user-id (get-in request [:session :user-id])})}
                        {:route     #"/api/re-auth"
                         :name      "reauth"
                         :success   (fn [in out]
                                      (and (:auth-re-auth? in)
                                           (nil? (:auth-re-auth? out))))
                         :fail-info (fn [request]
                                      {:user-id (get-in request [:session :user-id])})}
                        {:route     #"/escalate"
                         :name      "escalate"
                         :success   (fn [in out]
                                      (and (:limited-access? in)
                                           (nil? (:limited-access? out))))
                         :fail-info (fn [request]
                                      {:user-id (get-in request [:session :user-id])})}
                        {:route     #"/re-auth-ajax"
                         :name      "reauth"
                         :success   (fn [in out]
                                      (and (:auth-re-auth? in)
                                           (nil? (:auth-re-auth? out))))
                         :fail-info (fn [request]
                                      {:user-id (get-in request [:session :user-id])})}
                        {:route     #"/registration/[0-9]+/captcha"
                         :name      "captcha"
                         :success   (fn [in out]
                                      (and (not (:captcha-ok? in))
                                           (:captcha-ok? out)))
                         :fail-info (fn [request]
                                      {:username (get-in request [:params :captcha])})}
                        {:method         :get
                         :name           "quicklogin"
                         :route          #"/q/.+"
                         :success        (fn [_ out]
                                           (contains? out :user-id))
                         :fail           (fn [response]
                                           (not (contains? (:session response) :user-id)))
                         :delay-response (constantly (http-errors/too-many-requests-429 "Too many requests"))
                         :fail-info      (fn [request]
                                           {:username (:uri request)})}
                        {:method         :get
                         :name           "embedded"
                         :route          #"/embedded/create-session"
                         :success        (fn [_ out]
                                           (contains? out :user-id))
                         :fail           (fn [response]
                                           (not (contains? (:session response) :user-id)))
                         :delay-response (constantly (http-errors/too-many-requests-429 "Too many requests"))
                         :fail-info      (fn [request]
                                           {:username (get-in request [:params :uid])})}]]
    (->> attack-routes
         (map #(merge default-config %))
         (filter #(and (= (:request-method request) (:method %))
                       (re-matches (:route %) (:uri request))))
         (first))))

(defn attack-detector-mw
  [handler request]
  (if-let [attack-vector (match-attack-vector request)]
    (if-let [delay (get-delay-time request)]
      (do
        (when-not config/test-mode?
          (log/info "Possible attack detected - delaying response"))
        ((:delay-response attack-vector) delay))
      (let [response (handler request)]
        (cond
          ((:fail attack-vector) response)
          (let [info (or ((:fail-info attack-vector) request)
                         {})]
            (register-failed-login! (:name attack-vector) request info)
            (delay-ip! request)
            (delay-global!)
            response)

          ((:success attack-vector) (:session request) (:session response))
          (do
            (register-successful-login! request)
            response)

          :else
          response)))
    (handler request)))

;;
;; TODO: Block user after too many failed login attempts
;;
; An idea on how to implement a function that monitors whether
; caller is registered as an attack vector.
;(defn stack-traces []
;  (->> (Thread/currentThread)
;       (.getStackTrace)
;       (map (juxt #(.getClassName %) #(.getMethodName %) #(.getLineNumber %)))
;       (map #(clojure.string/join ":" %))))
;
;(defn x [] (stack-traces))
;(defn y [] (x))