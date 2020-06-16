(ns bass4.test.bankid.mock-collect
  (:require [clojure.core.async
             :refer [>! <! <!! go chan timeout alts!! dropping-buffer thread]]
            [clj-http.client :as http]
            [bass4.utils :refer [json-safe filter-map kebab-case-keys]]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [bass4.utils :as utils]
            [bass4.bankid.services :as bankid-service]
            [bass4.bankid.session :as bankid-session]
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
    (let [info            (bankid-session/get-session-info uid)
          first-status-no (:status-no info)]
      ;; Make sure two collects are performed or session is completed
      ;; before returning
      (loop [info info cycle-count 0]
        (when (and (bankid-service/session-active? info)
                   (> 2 (- (:status-no info) first-status-no)))
          (recur (bankid-session/get-session-info uid) (inc cycle-count))))
      (bankid-session/get-session-info uid))))

(defn wrap-set-session-status!
  [collect-log set-session-status!]
  (fn [uid status-map]
    (set-session-status! uid status-map)
    (let [new-status (get @bankid-session/session-statuses uid)]
      (if-let [order-ref (:order-ref new-status)]
        (swap! collect-log (fn [logs]
                             (if (get logs order-ref)
                               (assoc-in logs [order-ref :last-info] new-status)
                               logs)))))))

(defn collect-logger
  [collect-log max-collects]
  (let [global-start-time (. System (nanoTime))]
    (fn [order-ref _]
      (when max-collects
        (log/debug "Collecting (max collects)"))
      (when-not (get @collect-log order-ref))
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
        (if (or (nil? max-collects) (> max-collects current-count))
          (do
            (swap! collect-log update-count-fn true)
            (backend/api-collect order-ref nil))
          {:order-ref order-ref :status :failed :hint-code :user-cancel})))))


(defn wrap-mock
  ([] (wrap-mock :immediate nil))
  ([collect-method] (wrap-mock collect-method nil))
  ([collect-method max-collects] (wrap-mock collect-method max-collects false))
  ([collect-method max-collects http-request?]
   (assert (contains? #{:immediate :manual :wait} collect-method))
   (fn [f & args]
     (let [collect-counts (atom {})]
       (binding [backend/mock-backend-sessions      (atom {})
                 bankid-service/bankid-auth         backend/api-auth
                 bankid-service/bankid-collect      (collect-logger collect-counts max-collects)
                 bankid-service/bankid-cancel       backend/api-cancel
                 bankid-session/log-bankid-event!   (constantly nil)
                 bankid-session/session-statuses    (atom {})
                 #_bankid-session/get-collected-info  #_(if (= :manual collect-method)
                                                          get-collected-info-mock
                                                          bankid-session/get-collected-info)
                 bankid-session/set-session-status! (wrap-set-session-status! collect-counts bankid-session/set-session-status!)
                 backend/*delay-collect*            http-request?]
         (apply f args))
       collect-counts))))

(defn analyze-mock-log
  [res]
  (let [to-msec        #(double (/ % 1000000.0))
        quarts         (fn [vs] (map #(utils/round-to (utils/quantile % vs) 2) [0.25 0.5 0.75]))
        m-sd-q         #(str (utils/round-to (utils/mean %) 2)
                             " (" (utils/round-to (utils/sd %) 2) ")"
                             "[" (apply str (interpose ", " (quarts %))) "]")
        logs           (vals @res)
        no             (count logs)
        complete       (map #(not (bankid-service/session-active? (:last-info %))) logs)
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


