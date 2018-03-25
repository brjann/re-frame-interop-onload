(ns bass4.test.core
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [mount.core :as mount]
            [bass4.db.core]
            [bass4.bass-locals :as locals]
            [bass4.utils :refer [map-map]]
            [clj-time.coerce :as tc]
            [clojure.string :as string]
            [clojure.test]
            [clojure.tools.logging :as log]
            [bass4.middleware.core :as mw]
            [bass4.services.attack-detector :as a-d]))

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
    #'locals/local-configs
    #'locals/common-config
    #'bass4.db.core/db-connections
    #'bass4.db.core/db-common
    #'bass4.i18n/i18n-map)
  (bass4.db.core/init-repl :bass4_test)
  (binding [clojure.test/*stack-trace-depth* 5
            mw/*skip-csrf*                   true]
    (f)))

(defn disable-attack-detector [f]
  (with-redefs [a-d/delay-time! (constantly nil)]
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

(defn log-return
  ([x]
   (log/debug x)
   x)
  ([x y]
   (log/debug y)
   x))