(ns user
  (:require [mount.core :as mount]
            bass4.core))

(defn start []
  (mount/start-without #'bass4.core/http-server
                       #'bass4.core/repl-server))

(defn stop []
  (mount/stop-except #'bass4.core/http-server
                     #'bass4.core/repl-server))

(defn restart []
  (stop)
  (start))


