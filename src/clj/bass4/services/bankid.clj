(ns bass4.services.bankid
  (:require [clojure.core.async
             :refer [>! <! go chan timeout]]
            [clj-http.client :as http]
            [bass4.utils :refer [json-safe filter-map]]
            [clojure.walk :as walk]
            [clojure.tools.logging :as log]
            [clj-time.core :as t])
  (:import (java.util UUID)))

(defn print-status
  [uid s]
  (println (str (subs (str uid) 0 4) " " s)))

#_(def collect-responses
    {:pending {:outstandingTransaction :RFA13}})

(def auth-params
  {:keystore         "/Users/brjljo/Dropbox/Plattform/bass4/BankID/keystore_with_private.jks"
   :keystore-pass    "changeit"
   :trust-store      "/Users/brjljo/Dropbox/Plattform/bass4/BankID/truststore.jks"
   :trust-store-pass "changeit"
   :content-type     :json})

(defn bankid-request
  [endpoint form-params]
  (try (let [response (http/post (str "https://appapi2.test.bankid.com/rp/v5/" endpoint)
                                 (merge auth-params {:form-params form-params}))]
         (when (= 200 (:status response))
           (-> response
               :body
               json-safe
               walk/keywordize-keys)))
       (catch Exception _ nil)))

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
  (swap! session-statuses #(assoc % uid {:status :launching :start-time (t/now)})))

(defn set-session-order-ref!
  [uid order-ref]
  (swap! session-statuses
         (fn
           [session-map]
           (let [status-map (get session-map uid)]
             (assoc session-map uid (merge status-map {:status :started :order-ref order-ref}))))))

(defn set-session-status!
  ([uid status] (set-session-status! uid status {}))
  ([uid status info]
   (print-status uid (str "status of uid =" status))
   (swap! session-statuses
          (fn
            [session-map]
            (let [status-map (get session-map uid)]
              (assoc session-map uid (merge status-map {:status status} info)))))))

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
          (if (seq response)
            (let [order-ref        (:orderRef response)
                  auto-start-token (:autoStartToken response)]
              (set-session-order-ref! uid order-ref)
              (while (contains? #{:started :pending} (get-session-status uid))
                (<! (timeout (poll-interval)))
                (let [collect-chan (collect-bankid uid order-ref)
                      response     (<! collect-chan)]
                  (if (seq response)
                    (set-session-status!
                      uid
                      (keyword (:status response))
                      {:hint-code       (keyword (:hintCode response))
                       :completion-data (:completionData response)})
                    (set-session-status! uid :failed)))))
            (set-session-status! uid :failed {:hint-code :launch-failed}))))
    uid))

;; TODO: Log requests
;; TODO: Web interface
;; TODO: Middleware
;; TODO: Success-return, fail-return
;; TODO: Implement BankID texts
;; TODO: Auto-launch BankID
;; TODO: Tests: general, different responses, old session removal...