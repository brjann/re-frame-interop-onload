(ns bass4.middleware.request-state
  (:require [bass4.db.core :as db]
            [bass4.utils :refer [filter-map time+ nil-zero?]]
            [clj-time.coerce :as tc]
            [bass4.request-state :as request-state]
            [clojure.string :as string]
            [clojure.string :as s]
            [clojure.tools.logging :as log]
            [bass4.config :as config]
            [bass4.http-utils :as h-utils]))



;; ----------------
;;  REQUEST STATE
;; ----------------

(defn save-log!
  [req-state request time method status response-size]
  (db/save-pageload! {:db-name         (:name req-state),
                      :remote-ip       (hash (h-utils/get-client-ip request)),
                      :sql-time        (when (:sql-times req-state)
                                         (/ (apply + (:sql-times req-state)) 1000)),
                      :sql-max-time    (when (:sql-times req-state)
                                         (/ (apply max (:sql-times req-state)) 1000)),
                      :sql-ops         (count (:sql-times req-state))
                      :user-id         (:user-id req-state),
                      :render-time     (/ time 1000),
                      :response-size   response-size,
                      :clojure-version (str "Clojure " (clojure-version)),
                      :error-count     (:error-count req-state)
                      :error-messages  (:error-messages req-state)
                      :source-file     (:uri request),
                      :session-start   (tc/to-epoch (:session-start req-state)),
                      :user-agent      (get-in request [:headers "user-agent"])
                      :method          method
                      :status          status
                      :info            (->> (:info req-state)
                                            (s/join "\n"))}))

;; I would like to place this in the request-state namespace, however
;; that creates a circular dependency because db also uses the request-state
;; Don't really know how to handle that...
(defn request-state
  [handler request]
  (binding [request-state/*request-state* (atom {})]
    (let [request   (if request-state/*request-host*
                      (assoc request :server-name request-state/*request-host*)
                      request)
          {:keys [val time]} (time+ (handler request))
          method    (name (:request-method request))
          status    (:status val)
          req-state (request-state/get-state)]
      ;; Only save if request is tied to specific database
      (when (and (:name req-state) (not config/test-mode?))
        (let [body      (:body val)
              body-size (if (string? body)
                          (count body)
                          0)]
          (save-log! req-state request time method status body-size)))
      ;;val
      (if (:debug-headers req-state)
        (assoc val :headers (assoc (:headers val) "X-Debug-Headers" (string/join "\n" (:debug-headers req-state))))
        val))))
