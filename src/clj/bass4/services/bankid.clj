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
  (swap!
    session-statuses
    #(filter-map filter-old-uids %)))

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
           [session-map]
           (let [old-map (get session-map uid)
                 new-map (merge
                           old-map
                           {:status :started}
                           status-map)]
             (assoc
               session-map
               uid
               (merge new-map
                      {:status (keyword (:status new-map))})))))
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

;; TODO: Log requests
;; TODO: Validate input to BankID launch
;; TODO: Web interface
;; TODO: Middleware
;; TODO: Success-return, fail-return
;; TODO: Implement BankID texts
;; TODO: Auto-launch BankID
;; TODO: Tests: general, different responses, old session removal...