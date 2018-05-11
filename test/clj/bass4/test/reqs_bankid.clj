(ns bass4.test.reqs-bankid
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [clojure.core.async :refer [>!! <!! go chan timeout alts!!]]
            [bass4.test.core :refer [test-fixtures debug-headers-text? log-return disable-attack-detector *s* pass-by]]
            [bass4.services.auth :as auth-service]
            [bass4.services.user :as user]
            [bass4.services.bankid :as bankid]
            [bass4.services.bankid-mock :as bankid-mock]
            [bass4.middleware.debug :as debug]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [bass4.services.attack-detector :as a-d]
            [clojure.data.json :as json]))


(use-fixtures
  :once
  test-fixtures)

(def ^:dynamic *poll-next*)

(use-fixtures
  :each
  (fn [f]
    (bankid-mock/clear-sessions!)
    (reset! bankid/session-statuses {})
    (let [poll-chan    (chan)
          collect-chan (chan)
          poll-timeout (fn [] (alts!! [poll-chan (timeout 5000)]))
          poll-next    (fn [x]
                         (>!! poll-chan :x)
                         (<!! collect-chan)
                         x)]
      (binding [bankid/*bankid-auth*    bankid-mock/api-auth
                bankid/*bankid-collect* bankid-mock/api-collect
                bankid/*bankid-cancel*  bankid-mock/api-cancel
                bankid/*poll-timeout*   poll-timeout
                bankid/*collect-chan*   collect-chan
                *poll-next*             poll-next]
        (f)))))

(defn user-opens-app!
  [x pnr]
  (bankid-mock/user-opens-app! pnr)
  x)

(defn user-cancels!
  [x pnr]
  (bankid-mock/user-cancels! pnr)
  x)

(defn user-authenticates!
  [x pnr]
  (bankid-mock/user-authenticates! pnr)
  x)

(defn user-advance-time!
  [x pnr secs]
  (bankid-mock/user-advance-time! pnr secs)
  x)

(defn test-response
  [response criterion]
  (let [body         (get-in response [:response :body])
        response-map (json/read-str body)
        sub-map      (select-keys response-map (keys criterion))]
    (is (= criterion sub-map)))
  response)

(deftest bankid-auth
  (let [pnr "191212121212"]
    (-> *s*
        (visit "/e-auth/bankid/launch"
               :request-method
               :post
               :params
               {:personnummer     pnr
                :redirect-success "/e-auth/bankid/success"
                :redirect-fail    "/e-auth/bankid/test"})
        (has (status? 302))
        (follow-redirect)
        (visit "/e-auth/bankid/collect" :request-method :post)
        (test-response {"status" "starting" "hint-code" "contacting-bankid"})
        (*poll-next*)
        (visit "/e-auth/bankid/collect" :request-method :post)
        (test-response {"status" "pending" "hint-code" "outstanding-transaction"})
        (user-opens-app! pnr)
        (visit "/e-auth/bankid/collect" :request-method :post)
        (test-response {"status" "pending" "hint-code" "outstanding-transaction"})
        (*poll-next*)
        (visit "/e-auth/bankid/collect" :request-method :post)
        (test-response {"status" "pending" "hint-code" "user-sign"})
        (user-authenticates! pnr)
        (*poll-next*)
        (visit "/e-auth/bankid/collect" :request-method :post)
        (follow-redirect)
        (test-response {"personnummer" pnr}))))

(deftest bankid-cancels
  (let [pnr "191212121212"]
    (-> *s*
        (visit "/e-auth/bankid/launch"
               :request-method
               :post
               :params
               {:personnummer     pnr
                :redirect-success "/e-auth/bankid/success"
                :redirect-fail    "/e-auth/bankid/test"})
        (has (status? 302))
        (follow-redirect)
        (visit "/e-auth/bankid/collect" :request-method :post)
        (test-response {"status" "starting" "hint-code" "contacting-bankid"})
        (*poll-next*)
        (visit "/e-auth/bankid/collect" :request-method :post)
        (test-response {"status" "pending" "hint-code" "outstanding-transaction"})
        (user-opens-app! pnr)
        (visit "/e-auth/bankid/collect" :request-method :post)
        (test-response {"status" "pending" "hint-code" "outstanding-transaction"})
        (user-cancels! pnr)
        (*poll-next*)
        (visit "/e-auth/bankid/collect" :request-method :post)
        (test-response {"status" "failed" "hint-code" "user-cancel"})
        (visit "/e-auth/bankid/reset")
        (follow-redirect)
        (visit "/e-auth/bankid/collect" :request-method :post)
        (test-response {"status" "error" "hint-code" "No uid in session"})
        (visit "/e-auth/bankid/status")
        (has (status? 400)))))

(deftest bankid-clicks-cancel
  (let [pnr "191212121212"]
    (-> *s*
        (visit "/e-auth/bankid/launch"
               :request-method
               :post
               :params
               {:personnummer     pnr
                :redirect-success "/e-auth/bankid/success"
                :redirect-fail    "/e-auth/bankid/test"})
        (has (status? 302))
        (follow-redirect)
        (visit "/e-auth/bankid/collect" :request-method :post)
        (test-response {"status" "starting" "hint-code" "contacting-bankid"})
        (*poll-next*)
        (visit "/e-auth/bankid/collect" :request-method :post)
        (test-response {"status" "pending" "hint-code" "outstanding-transaction"})
        (user-opens-app! pnr)
        (visit "/e-auth/bankid/collect" :request-method :post)
        (test-response {"status" "pending" "hint-code" "outstanding-transaction"})
        (visit "/e-auth/bankid/cancel")
        (*poll-next*)
        (visit "/e-auth/bankid/collect" :request-method :post)
        (test-response {"status" "error" "hint-code" "No uid in session"})
        (visit "/e-auth/bankid/status")
        (has (status? 400)))))

#_(defmacro x
    [& forms]
    (loop [x# (first forms)]
      ~x#
      (recur (rest forms))))

#_(macroexpand-1 '(x (* 2) (* 9)))

#_(deftest bankid-parallel)