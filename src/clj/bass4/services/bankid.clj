(ns bass4.services.bankid
  (:require [clojure.core.async
             :refer [>! <! <!! go chan timeout]]
            [clj-http.client :as http]
            [bass4.utils :refer [json-safe filter-map kebab-case-keys]]
            [clojure.walk :as walk]
            [clojure.tools.logging :as log]
            [clj-time.core :as t])
  (:import (java.util UUID)))

(defn print-status
  [uid & s]
  (log/debug (str (subs (str uid) 0 4) " " (apply str s))))

;; -------------------
;;   BANKID REQUESTS
;; -------------------

(def auth-params
  {:keystore         "/Users/brjljo/Dropbox/Plattform/bass4/BankID/keystore_with_private.jks"
   :keystore-pass    "changeit"
   :trust-store      "/Users/brjljo/Dropbox/Plattform/bass4/BankID/truststore.jks"
   :trust-store-pass "changeit"
   :content-type     :json})

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
  [endpoint form-params]
  (log/debug "XXXXXXX XXXXXX running request")
  (try (let [response (http/post (str "https://appapi2.test.bankid.com/rp/v5/" endpoint)
                                 (merge auth-params {:form-params form-params}))]
         (if (= 200 (:status response))
           (response-body response)
           (throw (ex-info "Not 200 response" response))))
       (catch Exception e
         (bankid-error e))))

(defn ^:dynamic bankid-auth
  [personnummer]
  (bankid-request "auth"
                  {"personalNumber" personnummer
                   "endUserIp"      "81.232.173.180"}))

(defn ^:dynamic bankid-collect
  [order-ref]
  (log/debug "XXXXXXXXXXX XXXXXXX real collect")
  (bankid-request "collect" {"orderRef" order-ref}))

(defn ^:dynamic bankid-cancel
  [order-ref]
  (bankid-request "cancel" {"orderRef" order-ref}))


;; -------------------
;;   BANKID SESSION
;; -------------------

(def session-statuses (atom {}))

(defn session-active?
  [info]
  (contains? #{:starting :started :pending} (:status info)))

(defn filter-old-uids
  [status-map]
  (let [start-time  (:start-time status-map)
        age-minutes (t/in-minutes (t/interval start-time (t/now)))]
    (> 10 age-minutes)))

(defn remove-old-sessions!
  "Deletes sessions older than 10 minutes."
  []
  (let [old-count (count @session-statuses)]
    (swap!
      session-statuses
      #(filter-map filter-old-uids %))
    (if (< old-count (count @session-statuses))
      (log/debug "Deleted " (- (count @session-statuses) old-count) " sessions."))))

(defn get-session-info
  [uid]
  (remove-old-sessions!)
  (get @session-statuses uid))

(defn ^:dynamic get-collected-info
  [uid]
  (get-session-info uid))

(defn create-session!
  [uid]
  (swap! session-statuses #(assoc % uid {:status :starting :start-time (t/now)})))

(defn set-session-status!
  [uid status-map]
  (swap! session-statuses
         (fn
           [all-sessions]
           (let [old-map (get all-sessions uid)
                 new-map (merge
                           old-map
                           {:status :started}
                           status-map)]
             (if (session-active? old-map)
               (assoc
                 all-sessions
                 uid
                 (merge new-map
                        {:status (keyword (:status new-map))}))
               all-sessions))))
  (print-status uid (str "status of uid =" (:status (get @session-statuses uid)))))

;; --------------------------
;;        BANKID API
;; --------------------------


(defn start-bankid-session
  [personnummer]
  (let [start-chan (chan)]
    (go
      (>! start-chan (or (bankid-auth personnummer) {})))
    start-chan))

(defn collect-bankid
  [order-ref]
  (let [collect-chan (chan)]
    (go
      (>! collect-chan (or (bankid-collect order-ref) {})))
    collect-chan))

(defn ^:dynamic collect-waiter
  "Poll once every 1.5 seconds.
  Should be between 1 and 2 according to BankID spec"
  [order-ref]
  (log/debug "Waiting 1500 ms")
  (<!! (timeout 1500)))

(defn ^:dynamic collect-complete
  [order-ref])

(defn launch-bankid
  [personnummer]
  (let [uid (UUID/randomUUID)]
    (create-session! uid)
    (go (let [start-chan (start-bankid-session personnummer)
              response   (<! start-chan)]
          (set-session-status! uid response)
          (let [order-ref (:order-ref response)]
            (while (session-active? (get-session-info uid))
              (collect-waiter order-ref)
              (let [collect-chan (collect-bankid order-ref)
                    response     (<! collect-chan)]
                (set-session-status!
                  uid
                  (if (nil? (:status response))
                    {:status     :error
                     :error-code :collect-returned-nil-status
                     :order-ref  order-ref}
                    response))
                ;; To make sure that test function
                ;; checks status after it has been set
                (print-status uid "Collector cycle completed")
                (collect-complete order-ref)))
            (print-status uid "Collect loop aborted"))))
    uid))

(defn cancel-bankid!
  [uid]
  (let [info (get-collected-info uid)]
    (when (session-active? info)
      (set-session-status! uid {:status :failed :hint-code :user-cancel})
      (bankid-cancel (:order-ref info))))
  nil)

;; TODO: Log requests
;; TODO: Generalize ajax post handler?
;; TODO: Validate personnummer to BankID launch
;; TODO: Web interface for production
;; TODO: Check that client calls collect regularly?
;; TODO: Success-return, fail-return
;; TODO: Auto-launch BankID
;; TODO: Tests: general, different responses, old session removal...
;; TODO: Timeout guard in go block?
;; TODO: Add cancel request when cancelling