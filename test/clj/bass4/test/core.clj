(ns bass4.test.core
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [mount.core :as mount]
            [bass4.db.core]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

(defn get-edn
  [edn]
  (-> (io/file (System/getProperty "user.dir") "test/test-edns" (str edn ".edn"))
      (slurp)
      (edn/read-string)))

(defn test-fixtures
  [f]
  (mount/start
    #'bass4.config/env
    #'bass4.db.core/db-configs
    #'bass4.i18n/i18n-map)
  #_(migrations/migrate ["migrate"] (select-keys env [:database-url]))
  (alter-var-root #'bass4.middleware.core/*skip-csrf* (constantly true))
  (bass4.db.core/init-repl :test)
  (binding [clojure.test/*stack-trace-depth* 5] (f)))

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