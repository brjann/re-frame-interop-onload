(ns bass4.test.bankid.mock-reqs-utils
  (:require [clojure.core.async :refer [chan put! alts!! timeout]]
            [bass4.test.bankid.mock-backend :as mock-backend]
            [bass4.test.bankid.mock-collect :as mock-collect]
            [bass4.bankid.session :as bankid-session]))


(def ^:dynamic collect-chan)

(defn reqs-fixtures [f]
  (binding [collect-chan              (chan)
            bankid-session/wait-fn    (fn [] collect-chan)
            bankid-session/debug-chan (chan)]
    ((mock-collect/wrap-mock :manual nil false) f)))

(defn user-opens-app!
  [x pnr]
  (mock-backend/user-opens-app! pnr)
  x)

(defn user-cancels!
  [x pnr]
  (mock-backend/user-cancels! pnr)
  x)

(defn user-authenticates!
  [x pnr]
  (mock-backend/user-authenticates! pnr)
  x)

(defn user-advance-time!
  [x pnr secs]
  (mock-backend/user-advance-time! pnr secs)
  x)


(defn wait
  [state]
  (alts!! [bankid-session/debug-chan (timeout 5000)])
  state)

(defn collect+wait
  [state]
  (put! collect-chan true)
  (alts!! [bankid-session/debug-chan (timeout 5000)])
  state)
