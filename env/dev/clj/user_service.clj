(ns user-service
  (:require [mount.core :as mount]
            bass4.core))

;; This file contains settings for the REPL
;; http://www.luminusweb.net/docs/components.md
;; Luminus provides an <app>.user namespace found in the env/dev/clj/user.clj file.
;; This namespace provides convenience functions for starting and stopping the
;; application states from the REPL:

(defn start []
  (mount/start-without #'bass4.core/http-server
                       #'bass4.core/repl-server))

(defn stop []
  (mount/stop-except #'bass4.core/http-server
                     #'bass4.core/repl-server))

(defn restart []
  (stop)
  (start))


