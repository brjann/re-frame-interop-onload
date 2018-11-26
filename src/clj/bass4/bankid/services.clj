(ns bass4.bankid.services
  (:require [clojure.core.async
             :refer [>! <! <!! go chan timeout thread alts! put! go-loop alt!]]
            [clj-http.client :as http]
            [bass4.utils :refer [json-safe filter-map kebab-case-keys map-map]]
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

(defn ^:dynamic bankid-collect
  [order-ref config-key]
  (bankid-request "collect" {"orderRef" order-ref} config-key))

(defn ^:dynamic bankid-cancel
  [order-ref config-key]
  (bankid-request "cancel" {"orderRef" order-ref} config-key))


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



;; --------------------------
;;        BANKID API
;; --------------------------


(defn start-bankid-session
  [personnummer user-ip config-key]
  (thread
    (or (bankid-auth personnummer user-ip config-key) {})))

(defn collect-bankid
  [order-ref config-key]
  (thread
    (or (bankid-collect order-ref config-key) {})))


;; Originally, the wait was in a separate function.
;; However, it needed to be a blocked go
;; (<!! (go (<! (timeout 1500))) to actually wait
;; and this blocked all threads when multiple loops
;; were running. Therefore, it's solved like this.
;; If the collect-waiter is not set by testing
;; environment, then the loop does the
;; (<! (timeout 1500)) itself.
(def ^:dynamic collect-waiter nil)
(defn launch-bankid
  [personnummer user-ip config-key wait-chan res-chan]
  (let [start-time (bankid-now)]
    (log/debug "Starting go loop for " personnummer)
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
              ;; alt! bindings are not recognized by Cursive
              info         (alt! collect-chan ([response] (merge
                                                            response
                                                            (when-not order-ref
                                                              {:status     :started
                                                               :config-key config-key})))
                                 (timeout 20000) ([_] {:status     :error
                                                       :error-code :collect-timeout
                                                       :order-ref  order-ref}))]
          (log/debug "Sending info through res-chan" info)
          (put! res-chan info)
          (if (session-active? info)
            (if-not (alt! (wait-chan) true
                          (timeout 5000) false)
              (throw (ex-info "Wait chan timed out" info))
              (do
                (log/debug "Send and wait completed")
                (recur (:order-ref info))))
            (log/debug "Collect loop completed")))))))


;; -------------------
;;   BANKID SESSION
;; -------------------


;; TODO: Generalize ajax post handler?
;; TODO: Web interface for production
;; TODO: Check that client calls collect regularly?
;; TODO: Tests: general, different responses, old session removal...
;; TODO: Async tests are failing again (after adding the logs?)