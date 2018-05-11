(ns bass4.services.bankid-mock
  (:require [clojure.core.async
             :refer [>! <! go chan timeout]]
            [clj-http.client :as http]
            [bass4.utils :refer [json-safe filter-map kebab-case-keys]]
            [clojure.tools.logging :as log]
            [clj-time.core :as t])
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
  (let [sessions        (:sessions all-sessions)
        by-personnummer (:by-personnummer all-sessions)
        new-session     {:personnummer personnummer
                         :order-ref    order-ref
                         :elapsed-time 0
                         :status       :pending
                         :hint-code    :outstanding-transaction}]
    {:sessions        (assoc sessions order-ref new-session)
     :by-personnummer (assoc by-personnummer personnummer order-ref)}))

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

(defn api-collect
  [order-ref]
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