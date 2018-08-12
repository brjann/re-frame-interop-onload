(ns bass4.test.bankid.mock-collect
  (:require [clojure.core.async
             :refer [>! <! <!! go chan timeout alts!! dropping-buffer thread]]
            [clj-http.client :as http]
            [bass4.utils :refer [json-safe filter-map kebab-case-keys]]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [bass4.services.bankid :as bankid]
            [bass4.test.bankid.mock-backend :as backend]
            [clojure.math.numeric-tower :as math])
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

(defn wrap-set-session-status!
  [collect-log set-session-status!]
  (fn [uid status-map]
    (set-session-status! uid status-map)
    (let [new-status (get @bankid/session-statuses uid)]
      (if-let [order-ref (:order-ref new-status)]
        (swap! collect-log (fn [logs]
                             (if (get logs order-ref)
                               (assoc-in logs [order-ref :last-info] new-status)
                               logs)))))))

(defn collect-logger
  [collect-log max-collects]
  (let [global-start-time (. System (nanoTime))]
    (fn [order-ref _]
      (when-not (get @collect-log order-ref)
        #_(log/debug "Started collect for" order-ref))
      (let [update-count-fn (fn [all-counts inc-count?]
                              (let [current-status (get all-counts order-ref {:start-time        (. System (nanoTime))
                                                                              :global-start-time global-start-time
                                                                              :count             0})]
                                (assoc
                                  all-counts
                                  order-ref
                                  (merge
                                    current-status
                                    {:last-time (. System (nanoTime))}
                                    (when inc-count?
                                      {:count (inc (:count current-status))})))))
            current-count   (get-in @collect-log [order-ref :count] 0)]
        (let [info (if (or (nil? max-collects) (> max-collects current-count))
                     (let [info (backend/api-collect order-ref nil)]
                       (swap! collect-log update-count-fn true)
                       info)
                     (let [info {:order-ref order-ref :status :failed :hint-code :user-cancel}]
                       (swap! collect-log update-count-fn false)
                       info))]
          (when-not (bankid/session-active? info)
            #_(log/debug "Collect finished for " order-ref))
          info)))))



(defn wrap-mock
  ([] (wrap-mock :immediate nil))
  ([collect-method] (wrap-mock collect-method nil))
  ([collect-method max-collects] (wrap-mock collect-method max-collects false))
  ([collect-method max-collects http-request?]
   (assert (contains? #{:immediate :manual :wait} collect-method))
   (fn [f & args]
     (let [collect-counts (atom {})]
       (binding [backend/mock-backend-sessions (atom {})
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
                 bankid/set-session-status!    (wrap-set-session-status! collect-counts bankid/set-session-status!)
                 backend/*delay-collect*       http-request?]
         (apply f args))
       collect-counts))))

(defn mean
  ([vs] (mean (reduce + vs) (count vs)))
  ([sm sz] (/ sm sz)))

(defn sd
  ([vs]
   (sd vs (count vs) (mean vs)))
  ([vs sz u]
   (Math/sqrt (/ (reduce + (map #(Math/pow (- % u) 2) vs))
                 sz))))

(defn quantile
  ([p vs]
   (let [svs (sort vs)]
     (quantile p (count vs) svs (first svs) (last svs))))
  ([p c svs mn mx]
   (let [pic (* p (inc c))
         k   (int pic)
         d   (- pic k)
         ndk (if (zero? k) mn (nth svs (dec k)))]
     (cond
       (zero? k) mn
       (= c (dec k)) mx
       (= c k) mx
       :else (+ ndk (* d (- (nth svs k) ndk)))))))

(defn round-to
  [v p]
  (let [x (* (math/expt 10 p) v)
        r (math/round x)]
    (double (/ r (math/expt 10 p)))))

(defn analyze-mock-log
  [res]
  (let [to-msec        #(double (/ % 1000000.0))
        quarts         (fn [vs] (map #(round-to (quantile % vs) 2) [0.25 0.5 0.75]))
        m-sd-q         #(str (round-to (mean %) 2)
                             " (" (round-to (sd %) 2) ")"
                             "[" (apply str (interpose ", " (quarts %))) "]")
        logs           (vals @res)
        no             (count logs)
        complete       (map #(not (bankid/session-active? (:last-info %))) logs)
        collect-counts (map :count logs)
        startup-times  (map #(to-msec (- (:start-time %) (:global-start-time %))) logs)
        end-times      (map #(to-msec (- (:last-time %) (:global-start-time %))) logs)
        run-times      (map #(to-msec (- (:last-time %) (:start-time %))) logs)]
    (println (str "Count: " no))
    (println (str "Complete: " (count (filter identity complete))))
    (println (str "Collect cycles: " (m-sd-q collect-counts)))
    (println (str "Startup time: " (m-sd-q startup-times)))
    (println (str "End times: " (m-sd-q end-times)))
    (println (str "Run times: " (m-sd-q run-times)))))

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


