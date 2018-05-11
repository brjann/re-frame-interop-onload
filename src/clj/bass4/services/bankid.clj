(ns bass4.services.bankid
  (:require [clojure.core.async
             :refer [>! <! go chan timeout]]
            [clj-http.client :as http]
            [bass4.utils :refer [json-safe filter-map kebab-case-keys]]
            [clojure.walk :as walk]
            [clojure.tools.logging :as log]
            [clj-time.core :as t])
  (:import (java.util UUID)))

(defn print-status
  [uid s]
  (println (str (subs (str uid) 0 4) " " s)))

;; --------------------------
;;  BANKID REQUESTS HANDLER
;; --------------------------

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
  (try (let [response (http/post (str "https://appapi2.test.bankid.com/rp/v5/" endpoint)
                                 (merge auth-params {:form-params form-params}))]
         (if (= 200 (:status response))
           (response-body response)
           (throw (ex-info "Not 200 response" response))))
       (catch Exception e
         (bankid-error e))))

(defn bankid-auth
  [personnummer]
  (bankid-request "auth"
                  {"personalNumber" personnummer
                   "endUserIp"      "81.232.173.180"}))

(defn bankid-collect
  [order-ref]
  (bankid-request "collect" {"orderRef" order-ref}))

(defn bankid-cancel
  [order-ref]
  (bankid-request "cancel" {"orderRef" order-ref}))

(defn start-bankid-session
  [personnummer]
  (let [start-chan (chan)]
    (go
      (>! start-chan (or (bankid-auth personnummer) {})))
    start-chan))

(defn collect-bankid
  [uid order-ref]
  (print-status uid "Collecting")
  (let [collect-chan (chan)]
    (go
      (>! collect-chan (or (bankid-collect order-ref) {})))
    collect-chan))

;; --------------------------
;;   BANKID SESSION HANDLER
;; --------------------------

(def session-statuses (atom {}))

(defn filter-old-uids
  [status-map]
  (let [start-time (:start-time status-map)
        age        (t/in-minutes (t/interval start-time (t/now)))]
    (> 10 age)))

(defn remove-old-sessions!
  "Deletes sessions older than 10 minutes."
  []
  (let [old-count (count @session-statuses)]
    (swap!
      session-statuses
      #(filter-map filter-old-uids %))
    (if (< old-count (count @session-statuses))
      (log/debug "Deleted " (- (count @session-statuses) old-count) " sessions."))))

(defn get-session-status
  [uid]
  (remove-old-sessions!)
  (get-in @session-statuses [uid :status]))

(defn get-session-info
  [uid]
  (remove-old-sessions!)
  (get @session-statuses uid))

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
             (if (contains? #{:starting :started :pending nil} (:status old-map))
               (assoc
                 all-sessions
                 uid
                 (merge new-map
                        {:status (keyword (:status new-map))}))
               all-sessions))))
  (print-status uid (str "status of uid =" (:status (get @session-statuses uid)))))

;; --------------------------
;;      BANKID LAUNCHER
;; --------------------------

(defn poll-interval
  "Poll once every 1.5 seconds.
  Should be between 1 and 2 according to BankID spec"
  []
  1500)

(defn launch-bankid
  [personnummer]
  (let [uid (UUID/randomUUID)]
    (create-session! uid)
    (go (let [start-chan (start-bankid-session personnummer)
              response   (<! start-chan)]
          (set-session-status! uid response)
          (let [order-ref (:order-ref response)]
            (while (contains? #{:started :pending} (get-session-status uid))
              (<! (timeout (poll-interval)))
              (let [collect-chan (collect-bankid uid order-ref)
                    response     (<! collect-chan)]
                (set-session-status! uid response))))))
    uid))

(defn cancel-bankid
  [uid]
  (set-session-status! uid {:status :failed :hint-code :user-cancel})
  (if-let [order-ref (:order-ref (get-session-info uid))]
    (bankid-request "cancel" {:orderRef order-ref})))

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