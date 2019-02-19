(ns bass4.middleware.debug
  (:require [bass4.sms-sender :as sms]
            [bass4.config :refer [env]]
            [bass4.email :refer [send-email! send-email*! is-email?]]
            [bass4.request-state :as request-state]
            [prone.middleware :as prone]
            [bass4.middleware.ajax-post :as ajax-post]
            [bass4.db.core :as db]
            [clojure.tools.logging :as log]
            [bass4.db-config :as db-config]))


(defn wrap-debug-exceptions
  "Catches request exceptions and returns prone page.
  Exceptions in ajax post requests are not caught."
  [handler]
  (fn [request]
    (if (and (db-config/debug-mode?)
             (not (ajax-post/ajax-post? request)))
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