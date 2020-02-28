(ns ^:eftest/synchronized
  bass4.test.reqs-bankid
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [clojure.core.async :refer [<! >! >!! <!! thread put! go chan timeout alts!! dropping-buffer go-loop]]
            [bass4.test.core :refer [test-fixtures
                                     debug-headers-text?
                                     log-return
                                     disable-attack-detector
                                     *s*
                                     pass-by
                                     ->!
                                     json-sub-map?]]
            [bass4.test.bankid.mock-collect :as mock-collect :refer [analyze-mock-log]]
            [bass4.test.bankid.mock-reqs-utils :as bankid-utils :refer [wait
                                                                        collect+wait
                                                                        user-opens-app!
                                                                        user-authenticates!
                                                                        user-cancels!]]
            [clojure.data.json :as json]
            [bass4.i18n :as i18n]
            [bass4.bankid.session :as bankid-session]
            [clojure.tools.logging :as log])
  (:import (java.util UUID)))


(use-fixtures
  :once
  test-fixtures)

(use-fixtures
  :each
  bankid-utils/reqs-fixtures)


(deftest test-some-map?-macro
  (-> {:response {:body (json/write-str {"x" "y" "w" "z"})}}
      (has (json-sub-map? {"x" "y" "w" "z"}))
      (assoc-in [:response :body] (json/write-str {"x" "z" "w" "y"}))
      (has (json-sub-map? {"x" "z" "w" "y"}))))


(defn test-bankid-auth
  [pnr]
  (-> *s*
      (visit "/debug/bankid-launch"
             :request-method
             :post
             :params
             {:personnummer     pnr
              :redirect-success "/debug/bankid-success"
              :redirect-fail    "/debug/bankid-test"})
      (has (status? 302))
      (follow-redirect)
      (wait)
      (visit "/e-auth/bankid/collect" :request-method :post)
      (has (json-sub-map? {"status" "starting" "hint-code" "contacting-bankid"}))
      (collect+wait)
      (visit "/e-auth/bankid/collect" :request-method :post)
      (has (json-sub-map? {"status" "pending" "hint-code" "outstanding-transaction"}))
      (user-opens-app! pnr)
      (collect+wait)
      (visit "/e-auth/bankid/collect" :request-method :post)
      (has (json-sub-map? {"status" "pending" "hint-code" "user-sign"}))
      (user-authenticates! pnr)
      (collect+wait)
      (visit "/e-auth/bankid/collect" :request-method :post)
      (follow-redirect)
      (has (json-sub-map? {"personnummer" pnr}))))

(defn test-bankid-cancels
  [pnr]
  (-> *s*
      (visit "/debug/bankid-launch"
             :request-method
             :post
             :params
             {:personnummer     pnr
              :redirect-success "/debug/bankid-success"
              :redirect-fail    "/debug/bankid-test"})
      (has (status? 302))
      (follow-redirect)
      (wait)
      (visit "/e-auth/bankid/collect" :request-method :post)
      (has (json-sub-map? {"status" "starting" "hint-code" "contacting-bankid"}))
      (user-opens-app! pnr)
      (collect+wait)
      (visit "/e-auth/bankid/collect" :request-method :post)
      (has (json-sub-map? {"status" "pending" "hint-code" "user-sign"}))
      (user-cancels! pnr)
      (collect+wait)
      (visit "/e-auth/bankid/collect" :request-method :post)
      (has (json-sub-map? {"status" "failed" "hint-code" "user-cancel"}))
      (visit "/e-auth/bankid/reset")
      (follow-redirect)
      (visit "/e-auth/bankid/collect" :request-method :post)
      (has (json-sub-map? {"status" "error" "hint-code" "No uid in session"}))
      (visit "/e-auth/bankid/status")
      (has (status? 302))
      (follow-redirect)
      (has (some-text? "No ongoing"))
      (visit "/e-auth/bankid/cancel")
      (has (status? 302))
      (follow-redirect)
      (has (some-text? "No ongoing"))))

(defn test-bankid-clicks-cancel
  [pnr]
  (-> *s*
      (visit "/debug/bankid-launch"
             :request-method
             :post
             :params
             {:personnummer     pnr
              :redirect-success "/debug/bankid-success"
              :redirect-fail    "/debug/bankid-test"})
      (has (status? 302))
      (follow-redirect)
      (wait)
      (visit "/e-auth/bankid/collect" :request-method :post)
      (has (json-sub-map? {"status" "starting" "hint-code" "contacting-bankid"}))
      (user-opens-app! pnr)
      (collect+wait)
      (visit "/e-auth/bankid/collect" :request-method :post)
      (has (json-sub-map? {"status" "pending" "hint-code" "user-sign"}))
      (visit "/e-auth/bankid/cancel")
      (collect+wait)
      (visit "/e-auth/bankid/collect" :request-method :post)
      (has (json-sub-map? {"status" "error" "hint-code" "No uid in session"}))
      (visit "/e-auth/bankid/status")
      (has (status? 302))
      (follow-redirect)
      (has (some-text? "No ongoing"))))

(defn test-bankid-concurrent
  [pnr]
  (let [s1            (atom *s*)
        s2            (atom *s*)
        collect-chan2 (chan)]
    (->! s1
         (visit "/debug/bankid-launch"
                :request-method
                :post
                :params
                {:personnummer     pnr
                 :redirect-success "/debug/bankid-success"
                 :redirect-fail    "/debug/bankid-test"})
         (has (status? 302))
         (follow-redirect)
         (wait)
         (visit "/e-auth/bankid/collect" :request-method :post)
         (has (json-sub-map? {"status" "starting" "hint-code" "contacting-bankid"}))
         (user-opens-app! pnr)
         (collect+wait)
         (visit "/e-auth/bankid/collect" :request-method :post)
         (has (json-sub-map? {"status" "pending" "hint-code" "user-sign"})))
    (binding [bankid-utils/collect-chan collect-chan2
              bankid-session/wait-fn    (fn [] bankid-utils/collect-chan)
              bankid-session/debug-chan (chan)]
      (->! s2
           (visit "/debug/bankid-launch"
                  :request-method
                  :post
                  :params
                  {:personnummer     pnr
                   :redirect-success "/debug/bankid-success"
                   :redirect-fail    "/debug/bankid-test"})
           (has (status? 302))
           (follow-redirect)
           (wait)
           (visit "/e-auth/bankid/collect" :request-method :post)
           (has (json-sub-map? {"status" "error" "error-code" "already-in-progress"}))
           (visit "/e-auth/bankid/collect" :request-method :post)
           (has (json-sub-map? {"status" "error" "error-code" "already-in-progress"}))))
    (->! s1
         (collect+wait)
         (visit "/e-auth/bankid/collect" :request-method :post)
         (has (json-sub-map? {"status" "failed" "hint-code" "cancelled"})))))

(defn test-bankid-ongoing
  [pnr]
  (-> *s*
      (visit "/debug/bankid-launch"
             :request-method
             :post
             :params
             {:personnummer     pnr
              :redirect-success "/debug/bankid-success"
              :redirect-fail    "/debug/bankid-test"})
      (has (status? 302))
      (follow-redirect)
      (visit "/login")
      (follow-redirect)
      (has (some-text? "Ongoing"))
      (follow (i18n/tr [:bankid/ongoing-return]))
      (has (some-text? "BankID"))
      (visit "/e-auth/bankid/cancel")
      (collect+wait)
      (visit "/e-auth/bankid/collect" :request-method :post)
      (has (json-sub-map? {"status" "error" "hint-code" "No uid in session"})))
  (-> *s*
      (visit "/debug/bankid-launch"
             :request-method
             :post
             :params
             {:personnummer     pnr
              :redirect-success "/debug/bankid-success"
              :redirect-fail    "/debug/bankid-test"})
      (has (status? 302))
      (follow-redirect)
      (wait)
      (visit "/e-auth/bankid/collect" :request-method :post)
      (visit "/login")
      (follow-redirect)
      (has (some-text? "Ongoing"))
      (follow (i18n/tr [:bankid/ongoing-cancel]))
      (follow-redirect)
      (collect+wait)
      (has (some-text? "Login"))
      (visit "/e-auth/bankid/status")
      (has (status? 302))
      (follow-redirect)
      (has (some-text? "No ongoing"))))

(deftest test-bankid-no-ongoing
  (-> *s*
      (visit "/e-auth/bankid/status")
      (has (status? 302))
      (follow-redirect)
      (has (some-text? "No ongoing"))
      (visit "/e-auth/bankid/cancel")
      (has (status? 302))
      (follow-redirect)
      (has (some-text? "No ongoing"))
      (visit "/e-auth/bankid/reset")
      (has (status? 302))
      (follow-redirect)
      (has (some-text? "No ongoing"))))

(deftest bankid-auth
  (test-bankid-auth "191212121212"))

(deftest bankid-cancels
  (test-bankid-cancels "191212121212"))

(deftest bankid-clicks-cancel
  (test-bankid-clicks-cancel "191212121212"))

(deftest bankid-clicks-concurrent
  (test-bankid-concurrent "191212121212"))

(deftest bankid-clicks-ongoing
  (test-bankid-ongoing "191212121212"))

(defn massive-reqs-test
  ([] (massive-reqs-test 10))
  ([n] (massive-reqs-test n false))
  ([n go?]
   (let [test-fns  [test-bankid-auth
                    test-bankid-cancels
                    test-bankid-clicks-cancel
                    test-bankid-concurrent]
         leaved    (apply interleave (mapv #(repeat n %) test-fns))
         start-pnr 190000000000
         pnrs      (mapv str (range start-pnr (+ start-pnr (* (count test-fns) n))))
         f-p       (map #(vector %1 %2) leaved pnrs)
         f-p-exec  (fn [f p]
                     (let [fn-chan (chan)]
                       (go
                         (>! fn-chan (f p)))
                       fn-chan))
         ;; (go (f p)) did absolutely not work
         ;; PROBABLY because of the trouble with
         ;; sync response to the test and the
         ;; async collect loop.
         ;;
         ;; So future will instead run the tests
         ;; in separate threads. Which is not test
         ;; of super-concurrency but nevertheless
         ;; a few simultaneous processes.
         ;;
         ;; Running to many requests seems to lead to
         ;; trouble with non-existent uids. Maybe because
         ;; of timeout?
         ;;
         ;; What have I learned regarding go-blocks?
         ;; <!! can lead to total block if there are too
         ;; many of them active at the same time.
         ;; Don't do it man.
         ;;
         ;; NEW THOUGHTS: The go blocks start synchronous
         ;; tests that do not park. Thus, there is no room
         ;; over for the go async blocks. This is probably
         ;; the problem.
         executor  #_(if go?
                       (fn []
                         (loop [f-p f-p]
                           (when (seq f-p)
                             (let [[f p] (first f-p)]
                               (println "Running loop test on " p)
                               (let [fn-chan (f-p-exec f p)]
                                 (go (<! fn-chan)))
                               (recur (rest f-p))))))
                       #_(fn []
                           (go-loop [f-p f-p]
                             (when (seq f-p)
                               (let [[f p] (first f-p)]
                                 (println "Running loop test on " p)
                                 (let [fn-chan (f-p-exec f p)]
                                   (<! fn-chan))
                                 (recur (rest f-p)))))))
                   (fn []
                     (loop [f-p f-p]
                       (when (seq f-p)
                         (let [[f p] (first f-p)]
                           (println "Running loop test on " p)
                           (binding [bankid-utils/collect-chan (chan)
                                     bankid-session/wait-fn    (fn [] bankid-utils/collect-chan)
                                     bankid-session/debug-chan (chan)]
                             (thread (f p)))
                           (recur (rest f-p))))))
         test-fn   #((mock-collect/wrap-mock :manual nil true) executor)]
     (test-fixtures test-fn))))

;; THE STRESS TESTS HAVE NOT BEEN REWRITTEN/TESTED FOR THE NEW API
(defn stress-1
  [x]
  (let [pnrs (repeatedly x #(UUID/randomUUID))]
    (doall
      (for [pnr pnrs]
        (do
          (bankid-session/launch-user-bankid pnr "127.0.0.1" :prod))))))

; Check that many processes can be launched in infinite loop
#_((wrap-mock :immediate) stress-1 1000)

; Abort infinite loop DOESN'T WORK ANYMORE!
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