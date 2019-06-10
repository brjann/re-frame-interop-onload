(ns bass4.middleware.debug
  (:require [bass4.config :refer [env]]
            [bass4.external-messages.email-sender :refer [send-email! send-email*! is-email?]]
            [prone.middleware :as prone]
            [clojure.tools.logging :as log]
            [bass4.db-config :as db-config]
            [bass4.http-utils :as h-utils]))


(defn wrap-prone-debug-exceptions
  "Catches request exceptions and returns prone page.
  Exceptions in ajax post requests are not caught."
  [handler]
  (fn [request]
    (if (and (db-config/debug-mode?)
             (not (h-utils/ajax? request)))
      ((prone/wrap-exceptions handler) request)
      (handler request))))

(def ^:dynamic *session-modification* nil)
(defn session-modification-wrapper
  [handler request]
  (if *session-modification*
    (let [session  (merge (:session request) *session-modification*)
          response (handler (assoc request :session session))]
      (assoc response :session (if (nil? (:session response))
                                 session
                                 (merge (:session response) *session-modification*))))
    (handler request)))

(defn wrap-session-modification
  [handler]
  (fn [request]
    (if (db-config/debug-mode?)
      (session-modification-wrapper handler request)
      (handler request))))