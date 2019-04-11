(ns bass4.test.core
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [mount.core :as mount]
            [clojure.core.async :refer [alts!! timeout]]
            [bass4.db.core]
            [bass4.db.sql-wrapper]
            [bass4.utils :refer [map-map]]
            [kerodon.core :refer :all]
            [kerodon.test]
            [bass4.handler :refer :all]
            [clj-time.coerce :as tc]
            [clojure.string :as string]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [bass4.middleware.core :as mw]
            [bass4.services.attack-detector :as a-d]
            [clj-time.core :as t]
            [bass4.db-config :as db-config]
            [bass4.middleware.debug :as mw-debug]
            [bass4.config :as config]
            [clojure.string :as str]
            [bass4.email :as email]
            [bass4.sms-sender :as sms]
            [bass4.instrument.validation :as i-validation]
            [clojure.data.json :as json]
            [bass4.request-state :as request-state]
            [bass4.db.core :as db]
            [net.cgrand.enlive-html :as enlive]
            [bass4.time :as b-time]
            [bass4.utils :as utils]))

(def s (atom nil))
(def ^:dynamic *s* nil)

(def test-now (atom nil))
(def test-current-time (atom nil))

(defn modify-session
  [s session]
  (binding [mw-debug/*session-modification* session]
    (visit s "/debug/nothing")))

(defmacro fix-time
  [body]
  `(do
     (reset! test-current-time (utils/current-time))
     (reset! test-now (t/now))
     (with-redefs
       [t/now (fn [] @test-now)]
       (binding [utils/current-time (fn [] @test-current-time)]
         ~body))))

(defn advance-time-s!
  ([secs]
   (swap! test-now #(t/plus % (t/seconds secs)))
   (swap! test-current-time #(+ % secs)))
  ([state secs]
   (advance-time-s! secs)
   state))

(defn advance-time-d!
  ([days]
   (swap! test-now #(t/plus % (t/days days)))
   (swap! test-current-time (fn [ct]
                              (let [ct-time  (tc/from-epoch ct)
                                    new-time (t/plus ct-time (t/days days))]
                                (tc/to-epoch new-time)))))
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
    ;#'bass4.db.core/metrics-reg
    #'bass4.db.core/db-connections
    #'bass4.db.core/db-common
    #'bass4.i18n/i18n-map)
  (when (nil? @s)
    (swap! s (constantly (session (app)))))
  (let [test-db (config/env :test-db)]
    (binding [email/*email-reroute*            :void
              sms/*sms-reroute*                :void
              clojure.test/*stack-trace-depth* 10
              mw/*skip-csrf*                   true
              config/test-mode?                true
              *s*                              @s
              i-validation/*validate-answers?  false
              request-state/*request-host*     (config/env :test-host)
              db-config/*local-config*         (merge db-config/local-defaults (get db-config/local-configs test-db))
              db/*db*                          @(get db/db-connections test-db)]
      (f))))

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
                                :expected msg-pred#}))))

(defmacro sub-map?
  [expected]
  `(fn [response# msg#]
     (let [body#         (get-in response# [:response :body])
           response-map# (json/read-str body#)
           sub-map#      (select-keys response-map# (keys ~expected))]
       (is (= ~expected sub-map#) msg#))
     response#))

(defn api-response
  [s]
  (-> s
      :enlive
      (enlive/texts)
      (first)
      (json/read-str :key-fn keyword)))

(defmacro api-response?
  ([expected]
   `(kerodon.test/validate =
                           api-response
                           ~expected
                           (~'api-response? ~expected)))
  ([transform expected]
   `(kerodon.test/validate =
                           (comp ~transform api-response)
                           ~expected
                           (~'api-response? ~expected))))

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

(defn log-api-response
  ([s]
   (log/debug (api-response s))
   s))

(defmacro ->! [a & forms]
  `(if-not (and (symbol? '~a)
                (= (class ~a) clojure.lang.Atom))
     (throw (Exception. "Arg a must be an atom and symbol"))
     (let [new-a# (-> @~a ~@forms)]
       (reset! ~a new-a#))))

