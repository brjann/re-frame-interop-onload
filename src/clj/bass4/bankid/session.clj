(ns bass4.bankid.session
  (:require [clojure.core.async
             :refer [>! <! <!! go chan timeout thread alts! go-loop alt! put!]]
            [bass4.utils :refer [json-safe filter-map kebab-case-keys map-map]]
            [bass4.bankid.services :as bankid-service]
            [bass4.db.core :as db]
            [bass4.config :refer [env]]
            [clojure.tools.logging :as log])
  (:import (java.util UUID)))

(def session-statuses (atom {}))

(defn remove-old-sessions!
  "Deletes sessions older than 10 minutes."
  []
  (let [old-count (count @session-statuses)]
    (swap!
      session-statuses
      #(filter-map bankid-service/session-not-timed-out? %))
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
                                         :start-time (bankid-service/bankid-now)
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
             (if (bankid-service/session-active? old-map)
               (assoc
                 all-sessions
                 uid
                 (merge new-map
                        {:status (keyword (:status new-map))}))
               all-sessions))))
  #_(let [info (get @session-statuses uid)]
      (print-status uid (str "status of uid =" (:status info)) " number " (:status-no info))))

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

(defn ^:dynamic wait-fn
  []
  (timeout 1500))

(def ^:dynamic debug-chan nil)

(defn launch-user-bankid
  [personnummer user-ip config-key]
  (let [res-chan (chan)
        uid      (UUID/randomUUID)]
    (create-session! uid)
    (log-bankid-event! {:uid uid :personal-number personnummer :status :before-loop})
    (bankid-service/launch-bankid personnummer user-ip config-key wait-fn res-chan)
    (go-loop []
      (let [info      (first (alts! [res-chan (timeout 20000)]))
            order-ref (:order-ref info)]
        (set-session-status!
          uid
          (if (nil? (:status info))
            {:status     :error
             :error-code :collect-returned-nil-status
             :order-ref  order-ref}
            info))
        (when debug-chan
          (put! debug-chan true))
        (log-bankid-event! (assoc info :uid uid))
        (if (bankid-service/session-active? info)
          (recur)
          (log-bankid-event! {:uid uid :status :loop-complete}))))
    uid))

(defn cancel-bankid!
  [uid]
  (let [info (get-collected-info uid)]
    (when (bankid-service/session-active? info)
      (set-session-status! uid {:status :failed :hint-code :user-cancel})
      (log-bankid-event! {:uid uid :order-ref (:order-ref info) :status :failed :hint-code :user-cancel})
      (bankid-service/bankid-cancel (:order-ref info) (:config-key info))))
  nil)