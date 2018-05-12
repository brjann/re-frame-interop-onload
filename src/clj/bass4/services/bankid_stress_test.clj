(ns bass4.services.bankid-stress-test
  (:require [bass4.services.bankid :as bankid]
            [bass4.services.bankid-mock :as bankid-mock]
            [clojure.core.async :refer [>!! <!! go chan timeout alts!! dropping-buffer]]
            [clojure.tools.logging :as log])
  (:import (java.util UUID)))

(def ^:dynamic *poll-next*)

(def collect-counts (atom {}))

(defn collect-counter
  [max-collects]
  (reset! collect-counts {})
  (let [global-start-time (. System (nanoTime))]
    (fn [order-ref]
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
            (bankid-mock/api-collect order-ref))
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
  ([manual-collect?] (wrap-mock manual-collect? nil))
  ([manual-collect? max-collects] (wrap-mock manual-collect? max-collects false))
  ([manual-collect? max-collects delay-collect?]
   (fn [f & args]
     (bankid-mock/clear-sessions!)
     (reset! bankid/session-statuses {})
     (let [poll-chan    (chan)
           collect-chan (if manual-collect?
                          (chan)
                          (chan (dropping-buffer 0)))
           poll-timeout (if manual-collect?
                          (fn [] (alts!! [poll-chan (timeout 5000)]))
                          (fn [] true))
           poll-next    (fn [x]
                          (>!! poll-chan :x)
                          (<!! collect-chan)
                          x)
           collect-fn   (if max-collects
                          (collect-counter max-collects)
                          bankid-mock/api-collect)]
       (binding [bankid/*bankid-auth*        bankid-mock/api-auth
                 bankid/*bankid-collect*     collect-fn
                 bankid/*bankid-cancel*      bankid-mock/api-cancel
                 bankid/*poll-timeout*       poll-timeout
                 bankid/*collect-chan*       collect-chan
                 *poll-next*                 poll-next
                 bankid-mock/*delay-collect* delay-collect?]
         (apply f args))))))

(defn stress-1
  [x]
  (let [pnrs (repeatedly x #(UUID/randomUUID))]
    (doall
      (for [pnr pnrs]
        (do
          (bankid/launch-bankid pnr))))))

;; No problem running 1000 requests at the same time
#_((wrap-mock false) stress-1 1000)
#_(reset! bankid/session-statuses {})

#_((wrap-mock false 1000) stress-1 100)
;; Note that fractions can be used as delay https://httpbin.org/delay/0.5
#_((wrap-mock false 10 true) stress-1 10)
#_((wrap-mock false 10 true) stress-1 30)