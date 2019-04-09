(ns bass4.session.timeout
  "Adapted from https://github.com/ring-clojure/ring-session-timeout"
  (:require [bass4.http-errors :as http-errors]
            [clojure.tools.logging :as log]
            [bass4.config :refer [env]]
            [clj-time.core :as t]
            [clojure.string :as str]))


(defn reset-re-auth
  [session]
  (dissoc session :auth-re-auth? :soft-timeout-at))

(defn current-time
  []
  (t/now))

(defn re-auth-timeout
  []
  (or (env :timeout-soft) (* 30 60)))

(defn- request-string
  "Return the request part of the request."
  [request]
  (str (:uri request)
       (when-let [query (:query-string request)]
         (str "?" query))))


(defn- should-re-auth?
  [session now soft-timeout-at]
  (cond
    (:auth-re-auth? session)
    true

    (nil? soft-timeout-at)
    false

    (t/after? now soft-timeout-at)
    true

    :else false))

(defn- re-auth-response
  [request session]
  (-> (http-errors/re-auth-440 (str "/re-auth?return-url=" (request-string request)))
      (assoc :session (assoc session :auth-re-auth? true))))

(defn normal-response
  [handler request session-in now]
  (let [response        (handler (assoc request :session (dissoc session-in :auth-re-auth?)))
        _               (log/debug (re-auth-timeout))
        soft-timeout-at (t/plus now (t/seconds (dec (re-auth-timeout)))) ; dec because comparison is strictly after
        session-out     (:session response)
        session-map     {:soft-timeout-at soft-timeout-at
                         :auth-re-auth?   (if (contains? session-out :auth-re-auth?)
                                            (:auth-re-auth? session-out)
                                            false)}
        new-session     (if (nil? session-out)
                          (merge session-in session-map)
                          (merge session-out session-map))]
    (assoc response :session new-session)))

(defn auth-re-auth-mw
  [handler]
  (fn [request]
    (if (or (str/starts-with? (:uri request) "/user/ui")
            (:external-login? (:session request)))
      (handler request)
      (let [session-in      (:session request)
            now             (current-time)
            soft-timeout-at (:soft-timeout-at session-in)
            re-auth?        (should-re-auth? session-in now soft-timeout-at)]
        (if re-auth?
          (re-auth-response request session-in)
          (normal-response handler request session-in now))))))

#_(defn- current-time []
  (quot (System/currentTimeMillis) 1000))

(defn wrap-hard-session-timeout*
  [handler request timeout timeout-response timeout-handler]
  (let [session  (:session request {})
        end-time (::hard-timeout session)]
    (log/debug "Current time" (current-time))
    (log/debug "Session ends" end-time)
    (if (and end-time (< end-time (current-time)))
      (assoc (or timeout-response (timeout-handler request)) :session nil)
      (when-let [response (handler request)]
        (let [session (:session response session)]
          (if (nil? session)
            response
            (let [end-time (+ (current-time) timeout)]
              (assoc response :session (assoc session ::hard-timeout end-time)))))))))

(defn wrap-hard-session-timeout
  "Middleware that times out idle sessions after a specified number of seconds.

  If a session is timed out, the timeout-response option is returned. This is
  usually a redirect to the login page. Alternatively, the timeout-handler
  option may be specified. This should contain a Ring handler function that
  takes the current request and returns a timeout response.

  The following options are accepted:

  :timeout          - the idle timeout in seconds (default 600 seconds)
  :timeout-response - the response to send if an idle timeout occurs
  :timeout-handler  - the handler to run if an idle timeout occurs"
  {:arglists '([handler options])}
  [handler {:keys [timeout timeout-response timeout-handler] :or {timeout 600}}]
  {:pre [(integer? timeout)
         (if (map? timeout-response)
           (nil? timeout-handler)
           (ifn? timeout-handler))]}
  (fn [request]
    (wrap-hard-session-timeout* handler request timeout timeout-response timeout-handler)))

(defn wrap-absolute-session-timeout
  "Middleware that times out sessions after a specified number of seconds,
  regardless of whether the session is being used or idle. This places an upper
  limit on how long a compromised session can be exploited.

  If a session is timed out, the timeout-response option is returned. This is
  usually a redirect to the login page. Alternatively, the timeout-handler
  option may be specified. This should contain a Ring handler function that
  takes the current request and returns a timeout response.

  The following options are accepted:

  :timeout          - the absolute timeout in seconds
  :timeout-response - the response to send if an absolute timeout occurs
  :timeout-handler  - the handler to run if an absolute timeout occurs"
  {:arglists '([handler options])}
  [handler {:keys [timeout timeout-response timeout-handler]}]
  {:pre [(integer? timeout)
         (if (map? timeout-response)
           (nil? timeout-handler)
           (ifn? timeout-handler))]}
  (fn [request]
    (let [session  (:session request {})
          end-time (::absolute-timeout session)]
      (if (and end-time (< end-time (current-time)))
        (assoc (or timeout-response (timeout-handler request)) :session nil)
        (when-let [response (handler request)]
          (let [session (:session response session)]
            (if (or (nil? session) (and end-time (not (contains? response :session))))
              response
              (let [end-time (or end-time (+ (current-time) timeout))
                    session  (assoc session ::absolute-timeout end-time)]
                (assoc response :session session)))))))))