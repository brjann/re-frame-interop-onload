(ns bass4.test.core
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [mount.core :as mount]
            [bass4.db.core]
            [bass4.utils :refer [map-map]]
            [kerodon.core :refer :all]
            [bass4.handler :refer :all]
            [clj-time.coerce :as tc]
            [clojure.string :as string]
            [clojure.test]
            [clojure.tools.logging :as log]
            [bass4.middleware.core :as mw]
            [bass4.services.attack-detector :as a-d]
            [clj-time.core :as t]
            [bass4.services.bankid :as bankid]
            [bass4.db-config :as db-config]
            [bass4.middleware.debug :as mw-debug]))

(def s (atom nil))
(def ^:dynamic *s* nil)

(def test-now (atom nil))

(defn modify-session
  [s session]
  (binding [mw-debug/*session-modification* session]
    (visit s "/debug/nothing")))

(defmacro fix-time
  [body]
  `(do
     (swap! test-now (constantly (t/now)))
     (with-redefs
       [t/now (fn [] @test-now)]
       ~body)))

(defn advance-time-s!
  ([secs]
   (swap! test-now (constantly (t/plus (t/now) (t/seconds secs)))))
  ([state secs]
   (advance-time-s! secs)
   state))

(defn advance-time-d!
  ([days]
   (swap! test-now (constantly (t/plus (t/now) (t/days days)))))
  ([state days]
   (advance-time-s! days)
   state))

(defn get-edn
  [edn]
  (let [res (-> (io/file (System/getProperty "user.dir") "test/test-edns" (str edn ".edn"))
                (slurp)
                (edn/read-string))]
    (if (list? res)
      (map (fn [m] (map-map #(if (= java.util.Date (class %)) (tc/from-date %) %) m)) res)
      res)))

(defn test-fixtures
  [f]
  (mount/start
    #'bass4.config/env
    #'db-config/local-configs
    #'db-config/common-config
    #'bass4.db.core/db-connections
    #'bass4.db.core/db-common
    #'bass4.i18n/i18n-map)
  (when (nil? @s)
    (swap! s (constantly (session (app)))))
  (bass4.db.core/init-repl :bass4_test)
  (reset! bankid/session-statuses {})
  (binding [mw-debug/*mail-reroute*          :header
            mw-debug/*sms-reroute*           :header
            clojure.test/*stack-trace-depth* 5
            mw/*skip-csrf*                   true
            *s*                              @s]
    (f)))

(defn disable-attack-detector [f]
  (with-redefs [a-d/get-delay-time         (constantly nil)
                a-d/register-failed-login! (constantly nil)]
    (f)))

(defn debug-headers-not-text?
  [response & strs]
  (let [headers (or (get-in response [:response :headers "X-Debug-Headers"]) "")]
    (clojure.test/do-report {:actual   headers
                             :type     (if (some #(.contains headers %) strs)
                                         :fail
                                         :pass)
                             :message  ""
                             :expected (str "Not any of " (string/join ", " strs))}))
  response)

(defn debug-headers-text?
  [response & strs]
  (let [headers (or (get-in response [:response :headers "X-Debug-Headers"]) "")]
    (clojure.test/do-report {:actual   headers
                             :type     (if (every? #(.contains headers %) strs)
                                         :pass
                                         :fail)
                             :message  ""
                             :expected (string/join ", " strs)}))
  response)

(defn not-text?
  [response text]
  (let [body (get-in response [:response :body])]
    (clojure.test/do-report {:actual   (str "has " text)
                             :type     (if (.contains body text)
                                         :fail
                                         :pass)
                             :message  ""
                             :expected (str "does not have " text)}))
  response)

(defmacro pass-by
  [x form]
  `((fn [] ~form ~x)))

(defn log-return
  ([x]
   (log/debug x)
   x)
  ([x y]
   (log/debug y)
   x))

(defn log-body
  ([x]
   (log/debug (get-in x [:response :body]))
   x))

(defmacro ->! [a & forms]
  `(if-not (and (symbol? '~a)
                (= (class ~a) clojure.lang.Atom))
     (throw (Exception. "Arg a must be an atom and symbol"))
     (let [new-a# (-> @~a ~@forms)]
       (reset! ~a new-a#))))