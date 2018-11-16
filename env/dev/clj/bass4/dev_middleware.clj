(ns bass4.dev-middleware
  (:require [ring.middleware.reload :as reload]
            [selmer.middleware :as selmer]))

(defn wrap-dev [handler]
  (-> handler
      reload/wrap-reload
      ;; For selmer parsing errors
      selmer/wrap-error-page))
