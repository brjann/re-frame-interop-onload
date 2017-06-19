(ns bass4.dev-middleware
  (:require [ring.middleware.reload :refer [wrap-reload]]
            [selmer.middleware :refer [wrap-error-page]]
            [prone.middleware :refer [wrap-exceptions]]))

(defn wrap-dev [handler]
  (-> handler
      wrap-reload
      ;; For selmer parsing errors
      wrap-error-page

      ;; Instead included in bass4.middleware, contingent on being in debug or development mode.
      ;wrap-exceptions
      ))
