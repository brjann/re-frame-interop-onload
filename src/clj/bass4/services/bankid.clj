(ns bass4.services.bankid
  (:require [clojure.core.async
             :refer [>! <! <!! go chan timeout thread alts! go-loop]]
            [clj-http.client :as http]
            [bass4.utils :refer [json-safe filter-map kebab-case-keys map-map]]
            [clojure.walk :as walk]
            [bass4.db.core :as db]
            [bass4.config :refer [env]]
            [clojure.tools.logging :as log]
            [clj-time.core :as t])
  (:import (java.util UUID)))

(defn print-status
  [uid & s]
  (log/debug (str (subs (str uid) 0 4) " " (apply str s))))

(defn ^:dynamic bankid-now
  []
  (t/now))

;; -------------------
;;   BANKID REQUESTS
;; -------------------

(defn response-body
  [response]
  (->> response
       :body
       json-safe
       kebab-case-keys))

(defn bankid-error
  [e]
  (let [data        (ex-data e)
        http-status (:status data)]
    (if-not http-status
      {:status    :exception
       :exception e}
      (merge
        {:status      :error
         :http-status http-status}
        (response-body data)))))

(defn bankid-request
  [endpoint form-params config-key]
  #_(log/debug "XXXXXXX XXXXXX running request")
  (try (let [bankid-config (bass4.db-config/db-setting [:bankid :configs config-key])
             cert-params   (:cert-params bankid-config)
             url           (:url bankid-config)
             response      (http/post (str url endpoint)
                                      (merge cert-params
                                             {:form-params  form-params
                                              :content-type :json}))]
         (if (= 200 (:status response))
           (response-body response)
           (throw (ex-info "Not 200 response" response))))
       (catch Exception e
         (bankid-error e))))

;; TODO: End user IP
(defn ^:dynamic bankid-auth
  [personnummer user-ip config-key]
  (bankid-request "auth"
                  {"personalNumber" personnummer
                   "endUserIp"      user-ip}
                  config-key))
;; "81.232.173.180"

(defn ^:dynamic bankid-collect
  [order-ref config-key]
  (bankid-request "collect" {"orderRef" order-ref} config-key))

(defn ^:dynamic bankid-cancel
  [order-ref config-key]
  (bankid-request "cancel" {"orderRef" order-ref} config-key))


;; -------------------
;;   BANKID SESSION
;; -------------------

(def session-statuses (atom {}))

(defn session-active?
  [info]
  ;; TODO: Why does "pending" leak through?
  (contains? #{:starting :started :pending "pending"} (:status info)))

(defn session-not-timed-out?
  ([status-map] (session-not-timed-out? status-map 600))
  ([status-map time-limit-secs]
   (let [start-time  (:start-time status-map)
         now         (bankid-now)
         age-seconds (if (t/before? now start-time)
                       ;; If now is before start-time, then we're
                       ;; in testing mode and it is screwing
                       ;; with the time. Return time-limit to delete
                       ;; this session.
                       time-limit-secs
                       (t/in-seconds (t/interval start-time now)))]
     (> time-limit-secs age-seconds))))

(defn remove-old-sessions!
  "Deletes sessions older than 10 minutes."
  []
  (let [old-count (count @session-statuses)]
    (swap!
      session-statuses
      #(filter-map session-not-timed-out? %))
    #_(if (< old-count (count @session-statuses))
        (log/debug "Deleted circa " (- old-count (count @session-statuses)) " sessions."))))

(defn get-session-info
  [uid]
  #_(print-status uid "Checking session info")
  (remove-old-sessions!)
  (get @session-statuses uid))

(defn ^:dynamic get-collected-info
  [uid]
  (get-session-info uid))

(defn create-session!
  [uid]
  (swap! session-statuses #(assoc % uid {:status     :starting
                                         :start-time (bankid-now)
                                         :status-no  0})))

(defn delete-session!
  [uid]
  (swap! session-statuses #(dissoc % uid)))

(defn ^:dynamic set-session-status!
  [uid status-map]
  (swap! session-statuses
         (fn
           [all-sessions]
           (let [old-map (get all-sessions uid)
                 new-map (merge
                           old-map
                           {:status :started :status-no (inc (or (:status-no old-map) 0))}
                           status-map)]
             (if (session-active? old-map)
               (assoc
                 all-sessions
                 uid
                 (merge new-map
                        {:status (keyword (:status new-map))}))
               all-sessions))))
  #_(let [info (get @session-statuses uid)]
      (print-status uid (str "status of uid =" (:status info)) " number " (:status-no info))))

;; --------------------------
;;        BANKID API
;; --------------------------


(defn start-bankid-session
  [personnummer user-ip config-key]
  #_(log/debug "Starting bankID session")
  (let [start-chan (chan)]
    (go
      (>! start-chan (or (bankid-auth personnummer user-ip config-key) {})))
    start-chan))

(defn collect-bankid
  [order-ref config-key]
  (let [collect-chan (chan)]
    (go
      (>! collect-chan (or (bankid-collect order-ref config-key) {})))
    collect-chan))


;; Originally, the wait was in a separate function.
;; However, it needed to be a blocked go
;; (<!! (go (<! (timeout 1500))) to actually wait
;; and this blocked all threads when multiple loops
;; were running. Therefore, it's solved like this.
;; If the collect-waiter is not set by testing
;; environment, then the loop does the
;; (<! (timeout 1500)) itself.
(def ^:dynamic collect-waiter nil)

(defn ^:dynamic log-bankid-event!
  [response]
  (let [response (merge
                   (dissoc response :completion-data)
                   (:completion-data response)
                   {:personal-number (get-in
                                       response
                                       [:completion-data :user :personal-number]
                                       (:personal-number response))})
        cols     [:uid
                  :personal-number
                  :order-ref
                  :auto-start-token
                  :status
                  :hint-code
                  :http-status
                  :user
                  :device
                  :cert
                  :signature
                  :ocsp-response
                  :error-code
                  :details
                  :exception]
        other    {:other (str (apply dissoc response cols))}
        empty    (zipmap cols (repeat (count cols) nil))
        stringed (map-map str (select-keys response cols))]
    (db/log-bankid! (merge empty
                           stringed
                           other))))

(defn launch-bankid
  [personnummer user-ip config-key wait-chan res-chan]
  (let [uid        (UUID/randomUUID)
        start-time (bankid-now)]
    (log-bankid-event! {:uid uid :personal-number personnummer :status :before-loop})
    #_(print-status uid "Creating session for " personnummer)
    (create-session! uid)
    (log/debug "Starting go loop")
    (go-loop [order-ref nil]
      (log/debug "Collect cycle")
      (if-not (session-not-timed-out? {:start-time start-time} 300)
        (do
          (log/debug "Session timed out")
          (>! res-chan {:status     :error
                        :error-code :loop-timeout}))
        (let [collect-chan (if-not order-ref
                             (start-bankid-session personnummer user-ip config-key)
                             (collect-bankid order-ref config-key))
              response     (merge
                             (when-not order-ref
                               {:status     :started
                                :config-key config-key})
                             (<! collect-chan))
              order-ref    (:order-ref response)]
          (>! res-chan response)

          ;; Poll once every 1.5 seconds.
          ;; Should be between 1 and 2 according to BankID spec
          (if (nil? collect-waiter)
            (do
              (log/debug "Waiting 1500 ms")
              (<! (wait-chan)))
            (collect-waiter uid))
          (if (session-active? response)
            (recur order-ref)
            (do
              (log-bankid-event! {:uid uid :status :loop-complete})
              (print-status uid "Collect loop completed"))))))
    uid))

(defn launch-user-bankid
  [personnummer user-ip config-key]
  (let [res-chan (chan)]
    (let [uid (launch-bankid personnummer user-ip config-key #(timeout 1500) res-chan)]
      (go-loop []
        (let [response  (<! res-chan)
              order-ref (:order-ref response)]
          (set-session-status!
            uid
            (if (nil? (:status response))
              {:status     :error
               :error-code :collect-returned-nil-status
               :order-ref  order-ref}
              response))
          (log-bankid-event! (assoc response :uid uid))
          (recur)))
      uid)))

(defn cancel-bankid!
  [uid]
  (let [info (get-collected-info uid)]
    (when (session-active? info)
      (set-session-status! uid {:status :failed :hint-code :user-cancel})
      (log-bankid-event! {:uid uid :order-ref (:order-ref info) :status :failed :hint-code :user-cancel})
      (bankid-cancel (:order-ref info) (:config-key info))))
  nil)

;; TODO: Generalize ajax post handler?
;; TODO: Web interface for production
;; TODO: Check that client calls collect regularly?
;; TODO: Tests: general, different responses, old session removal...
;; TODO: Async tests are failing again (after adding the logs?)