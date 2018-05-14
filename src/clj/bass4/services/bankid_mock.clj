(ns bass4.services.bankid-mock
  (:require [clojure.core.async
             :refer [>! >!! <! <!! go chan timeout alts!! dropping-buffer]]
            [clj-http.client :as http]
            [bass4.utils :refer [json-safe filter-map kebab-case-keys]]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [bass4.services.bankid :as bankid])
  (:import (java.util UUID)))




(def bankid-message-map
  {:pending {:outstanding-transaction {:auto   :rfa13
                                       :manual :rfa1
                                       :else   :rfa1}
             :no-client               {:auto   :rfa1
                                       :manual :rfa1
                                       :else   :rfa1}
             :started                 {:without-start-token :rfa14
                                       :with-start-token    :rfa15
                                       :else                :rfa14}
             :else                    :rfa21}
   :failed  {:expired-transaction :rfa8
             :certificate-err     :rfa16
             :start-failed        :rfa17
             :else                :rfa22}
   :error   {400   {:invalid-parameters :exception
                    :else               :rfa22}
             401   :exception
             404   :exception
             408   :rfa5
             415   :exception
             500   :rfa5
             503   :rfa5
             :else :exception}})




;; Invalid parameter. Invalid use of method. Using an orderRef that previously resulted in completed. The order cannot be collected twice.
;; Using an orderRef that previously resulted in failed. The order cannot be collected twice.
;; Using an orderRef that is too old. completed orders can only be collected up to 3 minutes and failed orders up to 5 minutes. Details are found in details.

;; -------------------
;;      SESSIONS
;; -------------------

(def mock-sessions (atom {}))

(defn add-session-map
  [all-sessions personnummer order-ref]
  (let [sessions                      (:sessions all-sessions)
        by-personnummer               (:by-personnummer all-sessions)
        manual-collect-chans          (:manual-collect-chans all-sessions)
        manual-collect-complete-chans (:manual-collect-complete-chans all-sessions)
        new-session                   {:personnummer personnummer
                                       :order-ref    order-ref
                                       :elapsed-time 0
                                       :status       :pending
                                       :hint-code    :outstanding-transaction}]
    {:sessions                      (assoc sessions order-ref new-session)
     :by-personnummer               (assoc by-personnummer personnummer order-ref)
     :manual-collect-chans          (assoc manual-collect-chans order-ref (chan))
     :manual-collect-complete-chans (assoc manual-collect-complete-chans order-ref (chan))}))

(defn create-session!
  [personnummer]
  (let [order-ref (UUID/randomUUID)]
    (swap! mock-sessions add-session-map personnummer order-ref)
    order-ref))

(defn update-session!
  [order-ref session]
  (swap!
    mock-sessions
    (fn [all-sessions]
      (let [old-session (get (:sessions all-sessions) order-ref)]
        (assoc-in all-sessions [:sessions order-ref] (merge
                                                       old-session
                                                       session)))))
  nil)

(defn session-by-personnummer
  [personnummer]
  (if-let [order-ref (get-in @mock-sessions [:by-personnummer personnummer])]
    (get-in @mock-sessions [:sessions order-ref])
    (throw (ex-info "Order for personnummer does not exist" personnummer))))

(defn update-session-by-personnummer
  [personnummer session]
  (if-let [order-ref (get-in @mock-sessions [:by-personnummer personnummer])]
    (update-session! order-ref session)
    (throw (ex-info "Order for personnummer does not exist" {:personnummer personnummer
                                                             :info         session}))))

(defn delete-session!
  [order-ref]
  (swap!
    mock-sessions
    (fn [all-sessions]
      (let [sessions        (:sessions all-sessions)
            by-personnummer (:by-personnummer all-sessions)
            personnummer    (get-in sessions [order-ref :personnummer])]
        {:sessions        (dissoc sessions order-ref)
         :by-personnummer (dissoc by-personnummer personnummer)}))))

(defn clear-sessions!
  []
  (reset! mock-sessions {}))

;; -------------------
;;      MOCK API
;; -------------------

;; Note that the API functions are not concurrency safe!


(defn already-in-progress
  [existing-order-ref]
  (update-session! existing-order-ref {:status    :failed
                                       :hint-code :cancelled})
  {:status        :error
   :http-status   400
   :error-code    :already-in-progress
   :error-details ""})

(defn api-auth
  [personnummer]
  (let [existing-order-ref (get-in @mock-sessions [:by-personnummer personnummer])
        existing-session   (get-in @mock-sessions [:sessions existing-order-ref])]
    (if (= :pending (:status existing-session))
      (already-in-progress existing-order-ref)
      {:order-ref (create-session! personnummer)})))

(defn no-such-order
  []
  {:status      :error
   :http-status 400
   :error-code  "invalidParameters"
   :details     "No such order"})

(defn timeout!
  [order-ref]
  (delete-session! order-ref)
  {:order-ref order-ref
   :status    :failed
   :hint-code :expired-transaction})

(defn timeout?
  [session]
  (let [start-time (:start-time session)
        age        (t/in-seconds (t/interval start-time (t/now)))]
    (< 180 age)))

(defn session-info
  [session order-ref]
  (let [status (:status session)]
    (when-not (= :pending status)
      (delete-session! order-ref))
    (cond
      (< 180 (:elapsed-time session))
      (timeout! order-ref)

      (contains? #{:pending :failed} status)
      (select-keys session [:order-ref :status :hint-code])

      (= :complete status)
      (select-keys session [:order-ref :status :completion-data])

      :else
      (throw (ex-info "Impossible status" session)))))

(def ^:dynamic *delay-collect* false)

(defn api-collect
  [order-ref]
  (when *delay-collect*
    (http/get "https://httpbin.org/delay/1"))
  (let [session (get-in @mock-sessions [:sessions order-ref])]
    (if (nil? session)
      (no-such-order)
      (session-info session order-ref))))

(defn api-cancel
  [order-ref]
  (let [session (get-in @mock-sessions [:sessions order-ref])]
    (if (nil? session)
      (no-such-order)
      (delete-session! session))))


;; -------------------
;;   MANUAL COLLECT
;; -------------------


(defn manual-collect-waiter
  [order-ref]
  (log/debug "Waiting for manual collect.")
  (let [force-chan (get-in @mock-sessions [:manual-collect-chans order-ref])
        res        (alts!! [force-chan (timeout 1000)])]
    (when (nil? (first res))
      (log/debug "Manual chan timed out")))
  (log/debug "Manual collect received"))

(defn force-collect
  [uid order-ref]
  (let [force-chan    (get-in @mock-sessions [:manual-collect-chans order-ref])
        complete-chan (get-in @mock-sessions [:manual-collect-complete-chans order-ref])]
    (log/debug "Forcing collect")
    (go (>! force-chan order-ref))
    (log/debug "Forced collect completed. Waiting for collect completed response")
    (let [res (alts!! [complete-chan (timeout 1000)])]
      (when (nil? (first res))
        (log/debug "Complete chan timed out")))
    (log/debug "Collect completed response received")
    (bankid/get-session-info uid)))

(defn api-get-collected-info
  [uid]
  (log/debug "Fake get collected info")
  (let [info (bankid/get-session-info uid)]
    (if (bankid/session-active? info)
      (force-collect uid (:order-ref info))
      info)))

(defn manual-collect-complete
  [order-ref]
  (log/debug "Sending signal that manual collect is completed")
  (log/debug @mock-sessions)
  (if-let [complete-chan (get-in @mock-sessions [:manual-collect-complete-chans order-ref])]
    (go (>! complete-chan order-ref))
    (log/debug "Complete chan for" order-ref "not available")))

;; -------------------
;;     USER ACTIONS
;; -------------------

(defn user-opens-app!
  [personnummer]
  (update-session-by-personnummer
    personnummer
    {:hint-code :user-sign}))

(defn user-cancels!
  [personnummer]
  (update-session-by-personnummer
    personnummer
    {:status    :failed
     :hint-code :user-cancel}))

(defn user-authenticates!
  [personnummer]
  (update-session-by-personnummer
    personnummer
    {:status          :complete
     :completion-data {:user {:personal-number personnummer
                              :given-name      "Johan"
                              :surname         "Bjureberg"}}}))

(defn user-advance-time!
  [personnummer seconds]
  (let [session      (session-by-personnummer personnummer)
        elapsed-time (:elapsed-time session)]
    (update-session-by-personnummer
      personnummer
      {:elapsed-time (+ elapsed-time seconds)})))



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
            (api-collect order-ref))
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
     (log/debug "XXXXClearing sessions")
     (clear-sessions!)
     (reset! bankid/session-statuses {})
     (let [collect-chan (if manual-collect?
                          (chan)
                          (chan (dropping-buffer 0)))
           collect-fn   (if max-collects
                          (collect-counter max-collects)
                          api-collect)]
       (binding [bankid/bankid-auth        api-auth
                 bankid/bankid-collect     collect-fn
                 bankid/bankid-cancel      api-cancel
                 bankid/*collect-timeout*  (if manual-collect?
                                             manual-collect-waiter
                                             bankid/*collect-timeout*)
                 bankid/collect-complete   (if manual-collect?
                                             manual-collect-complete
                                             bankid/collect-complete)
                 bankid/get-collected-info (if manual-collect?
                                             api-get-collected-info
                                             bankid/get-collected-info)
                 *delay-collect*           delay-collect?]
         (apply f args))))))

(defn stress-1
  [x]
  (let [pnrs (repeatedly x #(UUID/randomUUID))]
    (doall
      (for [pnr pnrs]
        (do
          (bankid/launch-bankid pnr))))))


;#_((wrap-mock false) stress-1 1000)
;#_(reset! bankid/session-statuses {})
;
;#_((wrap-mock false 1000) stress-1 100)
;;; Note that fractions can be used as delay https://httpbin.org/delay/0.5
;#_((wrap-mock false 10 true) stress-1 10)
;#_((wrap-mock false 10 true) stress-1 30)