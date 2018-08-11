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

#_(def collect-counts (atom {}))

(defn collect-logger
  [collect-counts max-collects]
  #_(reset! collect-counts {})
  (let [global-start-time (. System (nanoTime))]
    (fn [order-ref _]
      (let [update-count-fn (fn [all-counts info inc-count?]
                              (let [current-status (get all-counts order-ref {:start-time        (. System (nanoTime))
                                                                              :global-start-time global-start-time
                                                                              :count             0})]
                                (assoc
                                  all-counts
                                  order-ref
                                  (merge
                                    current-status
                                    {:last-time   (. System (nanoTime))
                                     :last-status info}
                                    (when inc-count?
                                      {:count (inc (:count current-status))})))))
            current-count   (get-in @collect-counts [order-ref :count] 0)]
        (if (or (nil? max-collects) (> max-collects current-count))
          (let [info (backend/api-collect order-ref nil)]
            (swap! collect-counts update-count-fn info true)
            info)
          (let [info {:order-ref order-ref :status :failed :hint-code :user-cancel}]
            (swap! collect-counts update-count-fn info false)
            info))))))



(defn wrap-mock
  ([] (wrap-mock :immediate nil))
  ([collect-method] (wrap-mock collect-method nil))
  ([collect-method max-collects] (wrap-mock collect-method max-collects false))
  ([collect-method max-collects http-request?]
   (assert (contains? #{:immediate :manual :wait} collect-method))
   (fn [f & args]
     #_(backend/clear-sessions!)
     #_(reset! bankid/session-statuses {})
     ;; TODO: Why does session statuses have to be dynamic?
     (let [collect-counts (atom {})]
       (binding [;bankid/session-statuses       (atom {})
                 backend/mock-backend-sessions (atom {})
                 bankid/bankid-auth            backend/api-auth
                 bankid/bankid-collect         (collect-logger collect-counts max-collects)
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
         (apply f args))
       collect-counts))))

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


