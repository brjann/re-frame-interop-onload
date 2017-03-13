(ns bass4.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[bass4 started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[bass4 has shut down successfully]=-"))
   :middleware identity})
