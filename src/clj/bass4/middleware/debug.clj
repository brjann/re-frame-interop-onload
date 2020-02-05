(ns bass4.middleware.debug
  (:require [prone.middleware :as prone]
            [bass4.http-utils :as h-utils]
            [bass4.client-config :as client-config]))


(defn wrap-prone-debug-exceptions
  "Catches request exceptions and returns prone page.
  Exceptions in ajax post requests are not caught."
  [handler]
  (fn [request]
    (if (and (client-config/debug-mode?)
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
    (if (client-config/debug-mode?)
      (session-modification-wrapper handler request)
      (handler request))))