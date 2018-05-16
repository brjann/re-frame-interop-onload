(ns bass4.test.reqs-bankid
  (:require [bass4.i18n]
            [clojure.test :refer :all]
            [bass4.handler :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [clojure.core.async :refer [>! >!! <!! go chan timeout alts!! dropping-buffer go-loop]]
            [bass4.test.core :refer [test-fixtures debug-headers-text? log-return disable-attack-detector *s* pass-by ->!]]
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

(use-fixtures
  :each
  (bankid-mock/wrap-mock true))

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

(defn test-bankid-auth
  [pnr]
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
      (test-response {"status" "pending" "hint-code" "outstanding-transaction"})
      (user-opens-app! pnr)
      (visit "/e-auth/bankid/collect" :request-method :post)
      (test-response {"status" "pending" "hint-code" "user-sign"})
      (user-authenticates! pnr)
      (visit "/e-auth/bankid/collect" :request-method :post)
      (follow-redirect)
      (test-response {"personnummer" pnr})))

(defn test-bankid-cancels
  [pnr]
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
      (test-response {"status" "pending" "hint-code" "outstanding-transaction"})
      (user-opens-app! pnr)
      (visit "/e-auth/bankid/collect" :request-method :post)
      (test-response {"status" "pending" "hint-code" "user-sign"})
      (user-cancels! pnr)
      (visit "/e-auth/bankid/collect" :request-method :post)
      (test-response {"status" "failed" "hint-code" "user-cancel"})
      (visit "/e-auth/bankid/reset")
      (follow-redirect)
      (visit "/e-auth/bankid/collect" :request-method :post)
      (test-response {"status" "error" "hint-code" "No uid in session"})
      (visit "/e-auth/bankid/status")
      (has (status? 400))))

(defn test-bankid-clicks-cancel
  [pnr]
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
      (test-response {"status" "pending" "hint-code" "outstanding-transaction"})
      (user-opens-app! pnr)
      (visit "/e-auth/bankid/collect" :request-method :post)
      (test-response {"status" "pending" "hint-code" "user-sign"})
      (visit "/e-auth/bankid/cancel")
      (visit "/e-auth/bankid/collect" :request-method :post)
      (test-response {"status" "error" "hint-code" "No uid in session"})
      (visit "/e-auth/bankid/status")
      (has (status? 400))))

(defn test-bankid-concurrent
  [pnr]
  (let [s1 (atom *s*)
        s2 (atom *s*)]
    (->! s1
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
         (test-response {"status" "pending" "hint-code" "outstanding-transaction"})
         (user-opens-app! pnr)
         (visit "/e-auth/bankid/collect" :request-method :post)
         (test-response {"status" "pending" "hint-code" "user-sign"}))
    (->! s2
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
         (test-response {"status" "error" "error-code" "already-in-progress"})
         #_(*poll-next*)
         (visit "/e-auth/bankid/collect" :request-method :post)
         (test-response {"status" "error" "error-code" "already-in-progress"}))
    (->! s1
         (visit "/e-auth/bankid/collect" :request-method :post)
         (test-response {"status" "failed" "hint-code" "cancelled"}))))

(deftest bankid-auth
  (test-bankid-auth "191212121212"))

(deftest bankid-cancels
  (test-bankid-cancels "191212121212"))

(deftest bankid-clicks-cancel
  (test-bankid-clicks-cancel "191212121212"))

(deftest bankid-clicks-concurrent
  (test-bankid-concurrent "191212121212"))

(defn massive-reqs-test
  ([] (massive-reqs-test 10))
  ([n]
   (let [test-fns       [test-bankid-auth
                         test-bankid-cancels
                         test-bankid-clicks-cancel
                         test-bankid-concurrent]
         leaved         (apply interleave (mapv #(repeat n %) test-fns))
         start-pnr      190000000000
         pnrs           (mapv str (range start-pnr (+ start-pnr (* (count test-fns) n))))
         f-p            (map #(vector %1 %2) leaved pnrs)
         test-completed (chan)
         trash-chan     (chan (dropping-buffer 0))
         executor       (fn []
                          ;; Future occupies all threads.
                          (loop [f-p f-p]
                            (when (seq f-p)
                              (let [[f p] (first f-p)]
                                (println "Running loop test on " p)
                                #_(f p)
                                #_(go
                                    (f p)
                                    #_(<!! (timeout 10))
                                    #_(>!! trash-chan (f p)))
                                (future (f p))
                                (recur (rest f-p))))))
         xx             #((bankid-mock/wrap-mock true) executor)]
     (test-fixtures xx))))