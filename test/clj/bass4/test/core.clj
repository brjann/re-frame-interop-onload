(ns bass4.test.core
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [mount.core :as mount]
            [clojure.core.async :refer [alts!! timeout]]
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
            [bass4.middleware.debug :as mw-debug]
            [bass4.config :as config]
            [clojure.string :as str]))

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
  #_(reset! bankid/session-statuses {})
  (binding [mw-debug/*mail-reroute*          :header
            mw-debug/*sms-reroute*           :header
            clojure.test/*stack-trace-depth* 10
            mw/*skip-csrf*                   true
            config/test-mode?                true
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

(defn poll-message-chan
  ([c] (poll-message-chan c 1))
  ([c n]
   (for [i (range n)]
     (let [[res _] (alts!! [c (timeout 1000)])]
       (when (nil? res)
         (throw (Exception. (str "Channel " i " timed out"))))
       (-> res
           :message
           (select-keys [:type :message]))))))

(defn poll-message-chan2
  ([c] (poll-message-chan c 1))
  ([c n]
   (let [messages (for [i (range n)]
                    (let [[res _] (alts!! [c (timeout 1000)])]
                      (when (nil? res)
                        (throw (Exception. (str "Channel " i " timed out"))))
                      res))]
     (into #{} (map #(if (= :error (:result %))
                       (throw (ex-info "Exception in message" %))
                       (-> %
                           :message
                           (select-keys [:type :message])))
                    messages)))))

(defn any-match?
  [msg-pred messages]
  (let [match? (fn [msg-pred message]
                 (when (and (= (first msg-pred) (:type message))
                            (str/includes? (:message message) (second msg-pred)))
                   msg-pred))]
    (some #(match? msg-pred %1) messages)))

(defmacro messages-are?
  [msg-preds messages]
  `(let [msg-preds# ~msg-preds
         messages#  ~messages
         res#       (into #{} (map #(any-match? %1 messages#) msg-preds#))]
     (doseq [msg-pred# msg-preds#]
       (clojure.test/do-report {:actual   messages#
                                :type     (if (contains? res# msg-pred#)
                                            :pass
                                            :fail)
                                :message  "Message not found"
                                :expected msg-preds#}))))

(defmacro pass-by
  [prev form]
  `((fn [] (let [x# ~prev]
             ~form
             x#))))

(defn log-return
  ([x]
   (log/debug x)
   x)
  ([x y]
   (log/debug y)
   x))

(defn log-status
  ([x]
   (log/debug (get-in x [:response :status]))
   x))

(defn log-response
  ([x]
   (log/debug (:response x))
   x))

(defn log-headers
  ([x]
   (log/debug (get-in x [:response :headers]))
   x))

(defn log-body
  ([x]
   (log/debug (get-in x [:response :body]))
   x))

(defn log-session
  ([s]
   (log-body (visit s "/debug/session"))
   s))

(defmacro ->! [a & forms]
  `(if-not (and (symbol? '~a)
                (= (class ~a) clojure.lang.Atom))
     (throw (Exception. "Arg a must be an atom and symbol"))
     (let [new-a# (-> @~a ~@forms)]
       (reset! ~a new-a#))))

