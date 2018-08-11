(ns bass4.test.bankid.mock-collect
  (:require [clojure.core.async
             :refer [>! <! <!! go chan timeout alts!! dropping-buffer thread]]
            [clj-http.client :as http]
            [bass4.utils :refer [json-safe filter-map kebab-case-keys]]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [bass4.services.bankid :as bankid]
            [bass4.test.bankid.mock-backend :as backend])
  (:import (java.util UUID)))



;; --------------------
;;   BLOCKING COLLECT
;; --------------------

(defn get-collected-info-mock
  [uid]
  (if (nil? uid)
    nil
    (let [info            (bankid/get-session-info uid)
          first-status-no (:status-no info)]
      ;; Make sure two collects are performed or session is completed
      ;; before returning
      (loop [info info cycle-count 0]
        (when (and (bankid/session-active? info)
                   (> 2 (- (:status-no info) first-status-no)))
          (recur (bankid/get-session-info uid) (inc cycle-count))))
      (bankid/get-session-info uid))))

(def collect-counts (atom {}))

(defn collect-counter
  [max-collects]
  (reset! collect-counts {})
  (let [global-start-time (. System (nanoTime))]
    (fn [order-ref _]
      (let [current-count (get-in @collect-counts [order-ref :count] 0)]
        (if (> max-collects current-count)
          (do
            (swap!
              collect-counts
              (fn [all-counts]
                (let [current-count (get-in all-counts [order-ref :count] 0)
                      start-time    (get-in all-counts [order-ref :start-time] (. System (nanoTime)))]
                  (assoc
                    all-counts
                    order-ref
                    {:count      (inc current-count)
                     :start-time start-time}))))
            (backend/api-collect order-ref nil))
          (do
            (swap!
              collect-counts
              (fn [all-counts]
                (let [current-count (get-in all-counts [order-ref :count])
                      current-time  (. System (nanoTime))
                      start-time    (get-in all-counts [order-ref :start-time])]
                  (assoc
                    all-counts
                    order-ref
                    {:count          current-count
                     :start-time     start-time
                     :end-time       current-time
                     :elapsed-time   (double (/ (- current-time start-time) 1000000.0))
                     :elapsed-global (double (/ (- current-time global-start-time) 1000000.0))}))))
            {:order-ref order-ref :status :failed :hint-code :user-cancel}))))))



(defn wrap-mock
  ([] (wrap-mock :immediate nil))
  ([collect-method] (wrap-mock collect-method nil))
  ([collect-method max-collects] (wrap-mock collect-method max-collects false))
  ([collect-method max-collects http-request?]
   (assert (contains? #{:immediate :manual :wait} collect-method))
   (fn [f & args]
     #_(backend/clear-sessions!)
     #_(reset! bankid/session-statuses {})
     (binding [backend/mock-backend-sessions (atom {})
               bankid/bankid-auth            backend/api-auth
               bankid/bankid-collect         (if max-collects
                                               (collect-counter max-collects)
                                               backend/api-collect)
               bankid/bankid-cancel          backend/api-cancel
               bankid/collect-waiter         (case collect-method
                                               (:immediate :manual)
                                               (constantly nil)

                                               :wait
                                               bankid/collect-waiter)
               bankid/get-collected-info     (if (= :manual collect-method)
                                               get-collected-info-mock
                                               bankid/get-collected-info)
               backend/*delay-collect*       http-request?]
       (apply f args)))))

(defn stress-1
  [x]
  (let [pnrs (repeatedly x #(UUID/randomUUID))]
    (doall
      (for [pnr pnrs]
        (do
          (bankid/launch-bankid pnr "127.0.0.1"))))))

; Check that many processes can be launched in infinite loop
#_((wrap-mock :immediate) stress-1 1000)

; Abort infinite loop
#_(reset! bankid/session-statuses {})

; Multiple processes with immediate and max 10 faked collects
#_((wrap-mock :immediate 10) stress-1 100)

; Multiple processes with immediate and max X http collects
#_((wrap-mock :immediate 10 true) stress-1 10)
#_((wrap-mock :immediate 10 true) stress-1 30)

; Multiple processes that wait
#_((wrap-mock :wait 10 true) stress-1 5)
#_((wrap-mock :wait 10 true) stress-1 10)

; Multiple processes that both wait and do http polling
; I.e., testing of real-life conditions.
#_((wrap-mock :wait 20 true) stress-1 10)
#_((wrap-mock :wait 100 true) stress-1 100)                 ;Takes a looong time but does not block.


