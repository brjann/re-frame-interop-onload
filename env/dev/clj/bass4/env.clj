(ns bass4.env
  (:require [selmer.parser :as parser]
            [clojure.tools.logging :as log]
            [bass4.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[bass4 started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[bass4 has shut down successfully]=-"))
   :middleware wrap-dev})
