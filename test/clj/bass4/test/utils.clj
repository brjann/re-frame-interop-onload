(ns bass4.test.utils
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [mount.core :as mount]
            [bass4.db.core]))

(defn get-edn
  [edn]
  (-> (io/file (System/getProperty "user.dir") "test/test-edns" (str edn ".edn"))
      (slurp)
      (edn/read-string)))

(defn test-fixtures
  [f]
  (mount/start
    #'bass4.config/env
    #'bass4.db.core/db-configs)
  #_(migrations/migrate ["migrate"] (select-keys env [:database-url]))
  (bass4.db.core/init-repl :db1)
  (f))