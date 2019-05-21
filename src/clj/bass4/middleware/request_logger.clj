(ns bass4.middleware.request-logger
  (:require [bass4.db.core :as db]
            [bass4.utils :refer [filter-map time+ nil-zero?]]
            [clj-time.coerce :as tc]
            [clojure.string :as string]
            [clojure.string :as s]
            [bass4.config :as config]
            [bass4.http-utils :as h-utils]
            [bass4.utils :as utils]))


(def ^:dynamic *request-state* nil)
(def ^:dynamic *request-host* nil)

(defn swap-state!
  ([key f] (swap-state! key f nil))
  ([key f val-if-empty]
   (when *request-state*
     (utils/swap-key! *request-state* key f val-if-empty))))

(defn add-to-state-key!
  [key v]
  (swap-state! key #(conj %1 v) []))

(defn set-state!
  [key val]
  (when *request-state*
    (utils/set-key! *request-state* key val)))

(defn record-error!
  [error]
  (swap-state! :error-count inc 0)
  (swap-state! :error-messages
               #(if %
                  (clojure.string/join "\n----------------\n" [% (str error)])
                  (str error))))

(defn get-state
  []
  @*request-state*)


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

(defn wrap-logger
  [handler request]
  (binding [*request-state* (atom {})]
    (let [request   (if *request-host*
                      (assoc request :server-name *request-host*)
                      request)
          {:keys [val time]} (time+ (handler request))
          response  val
          method    (name (:request-method request))
          status    (:status response)
          req-state (get-state)]
      ;; Only save if request is tied to specific database
      (when (and (:name req-state)
                 (not config/test-mode?)
                 (not (::no-log? response)))
        (let [body      (:body response)
              body-size (if (string? body)
                          (count body)
                          0)]
          (save-log! req-state request time method status body-size)))
      ;;val
      (let [response (dissoc response ::no-log?)]
        (if (:debug-headers req-state)
          (assoc response :headers (assoc (:headers response) "X-Debug-Headers" (string/join "\n" (:debug-headers req-state))))
          response)))))
