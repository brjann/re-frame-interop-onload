(ns bass4.services.bankid-mock
  (:require [clojure.core.async
             :refer [>! <! <!! go chan timeout alts!! dropping-buffer thread]]
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
  (log/debug "Creating session map for" personnummer)
  (let [sessions               (:sessions all-sessions)
        by-personnummer        (:by-personnummer all-sessions)
        collect-force-chans    (:collect-force-chans all-sessions)
        collect-complete-chans (:collect-complete-chans all-sessions)
        new-session            {:personnummer personnummer
                                :order-ref    order-ref
                                :elapsed-time 0
                                :status       :pending
                                :hint-code    :outstanding-transaction}]
    {:sessions               (assoc sessions order-ref new-session)
     :by-personnummer        (assoc by-personnummer personnummer order-ref)
     :collect-force-chans    (assoc collect-force-chans order-ref (chan))
     :collect-complete-chans (assoc collect-complete-chans order-ref (chan))}))

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
        (merge
          all-sessions
          {:sessions        (dissoc sessions order-ref)
           :by-personnummer (dissoc by-personnummer personnummer)})))))

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
  ([personnummer] (api-auth personnummer "127.0.0.1"))
  ([personnummer user-ip]
   (log/debug "Auth for" personnummer)
   (let [existing-order-ref (get-in @mock-sessions [:by-personnummer personnummer])
         existing-session   (get-in @mock-sessions [:sessions existing-order-ref])]
     (if (= :pending (:status existing-session))
       (already-in-progress existing-order-ref)
       {:order-ref (create-session! personnummer)}))))

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
    (http/get "https://httpbin.org/delay/0.1"))
  (let [session (get-in @mock-sessions [:sessions order-ref])]
    (if (nil? session)
      (no-such-order)
      (session-info session order-ref))))

(defn api-cancel
  [order-ref]
  (let [session (get-in @mock-sessions [:sessions order-ref])]
    (if (nil? session)
      (no-such-order)
      (delete-session! order-ref))))


;; -------------------
;;   MANUAL COLLECT
;; -------------------

(def collect-atoms (atom {}))
(def collect-chans (atom {}))


(def collector-states-atom (atom {}))

(defn collector-has-state?
  [collector-states uid state-key state-id]
  (contains? (get-in collector-states [uid state-key]) state-id))

(defn add-collector-state
  [collector-states uid new-state]
  (let [uid-state (get collector-states uid)
        started   (get uid-state :waiting #{})
        merged    (conj started new-state)]
    (assoc collector-states uid (merge
                                  uid-state
                                  {:waiting merged}))))

(defn move-collector-state
  [collector-states uid from-key to-key]
  (let [uid-state (get collector-states uid)
        from      (get uid-state from-key #{})
        to        (get uid-state to-key #{})
        merged    (clojure.set/union from to)]
    (assoc collector-states uid (merge
                                  uid-state
                                  {to-key   merged
                                   from-key #{}}))))

#_(def check-collect-running (atom {}))
#_(def check-wait-running (atom {}))

#_(def collect-loop-chans (atom {}))

#_(defn get-collect-loop-chan
    [uid]
    (when-not (get @collect-loop-chans uid)
      (log/debug "Creating loop collector for " uid)
      (swap! collect-loop-chans #(assoc % uid (chan (dropping-buffer 1)))))
    (get @collect-loop-chans uid))


(defn manual-collect-waiter
  [uid]
  (swap! collector-states-atom move-collector-state uid :waiting :complete))


(defn wait-for-collect-status
  [uid state-id]
  (while (and (bankid/session-active? (bankid/get-session-info uid))
              (not (collector-has-state? @collector-states-atom uid :complete state-id)))))

(defn api-get-collected-info
  [uid]
  (if (nil? uid)
    nil
    (let [state-id (UUID/randomUUID)]
      (log/debug "New state id" state-id)
      (swap! collector-states-atom add-collector-state uid state-id)
      (wait-for-collect-status uid state-id)
      (let [info (bankid/get-session-info uid)]
        (log/debug "Received new status" info)
        (if (contains? #{:starting :started} (:status info))
          (merge info
                 {:status    :pending
                  :hint-code :outstanding-transaction})
          info)))))

(defn manual-collect-complete
  [uid]
  (swap! collector-states-atom move-collector-state uid :waiting :complete))


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
  ([] (wrap-mock :immediate nil))
  ([collect-method] (wrap-mock collect-method nil))
  ([collect-method max-collects] (wrap-mock collect-method max-collects false))
  ([collect-method max-collects http-request?]
   (assert (contains? #{:immediate :manual :wait} collect-method))
   (fn [f & args]
     (log/debug "Clearing all sessions")
     (clear-sessions!)
     (reset! collector-states-atom {})
     (reset! bankid/session-statuses {})
     (binding [bankid/bankid-auth           api-auth
               bankid/bankid-collect        (if max-collects
                                              (collect-counter max-collects)
                                              api-collect)
               bankid/bankid-cancel         api-cancel
               bankid/collect-waiter        (case collect-method
                                              :immediate
                                              (fn [uid] (log/debug "Collecting info for" uid))

                                              :manual
                                              manual-collect-waiter

                                              :wait
                                              bankid/collect-waiter)
               bankid/collect-loop-complete (if (= :manual collect-method)
                                              manual-collect-complete
                                              (constantly nil))
               bankid/get-collected-info    (if (= :manual collect-method)
                                              api-get-collected-info
                                              bankid/get-collected-info)
               *delay-collect*              http-request?]
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


