(ns bass4.session.timeout
  "Adapted from https://github.com/ring-clojure/ring-session-timeout"
  (:require [clojure.string :as str]
            [bass4.http-errors :as http-errors]
            [bass4.config :refer [env]]
            [bass4.session.utils :as session-utils]
            [bass4.utils :as utils]
            [ring.util.http-response :as http-response]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [bass4.config :as config]
            [bass4.http-utils :as h-utils]))

(def ^:dynamic *in-session?* false)

(defn timeout-hard-limit
  []
  (config/env :timeout-hard))

(defn timeout-hard-soon-limit
  []
  (config/env :timeout-hard-soon))

(defn timeout-re-auth-limit
  []
  (config/env :timeout-soft))

;; -------------------
;;   RE-AUTH TIMEOUT
;; -------------------

(defn re-auth-timeout-map
  []
  {::re-auth-timeout-at (+ (utils/current-time)
                           (timeout-re-auth-limit))})

(defn reset-re-auth
  [session]
  (merge session
         {:auth-re-auth? false}
         (re-auth-timeout-map)))

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

    (>= now soft-timeout-at)
    true

    :else false))

(defn- re-auth-response
  [request session]
  (-> (http-errors/re-auth-440 (str "/re-auth?return-url=" (request-string request)))
      (assoc :session (assoc session :auth-re-auth? true))))

(defn- no-re-auth-response
  [handler request session-in]
  (let [response (handler (assoc request :session (dissoc session-in :auth-re-auth?)))]
    (session-utils/assoc-out-session response session-in (re-auth-timeout-map))))

(defn- wrap-session-re-auth-timeout*
  [handler request]
  (if (or (str/starts-with? (:uri request) "/user/ui")
          (:external-login? (:session request)))
    (handler request)
    (let [session-in         (:session request)
          now                (utils/current-time)
          re-auth-timeout-at (::re-auth-timeout-at session-in)
          re-auth?           (should-re-auth? session-in now re-auth-timeout-at)]
      (if re-auth?
        (re-auth-response request session-in)
        (no-re-auth-response handler request session-in)))))

(defn wrap-session-re-auth-timeout
  ([handler]
   (fn [request]
     (wrap-session-re-auth-timeout* handler request))))

;; ------------------------
;;    SESSION STATUS API
;; ------------------------

(defn- session-status
  [request hard-timeout-at hard-timeout?]
  (let [session            (:session request)
        now                (utils/current-time)
        re-auth-timeout-at (::re-auth-timeout-at session)
        res                (when-not (or (empty? (dissoc session ::hard-timeout-at))
                                         hard-timeout?)
                             {:hard    (when hard-timeout-at
                                         (- hard-timeout-at now))
                              :re-auth (when (and re-auth-timeout-at
                                                  (:user-id session)
                                                  (not (:external-login? session)))
                                         (max 0 (- re-auth-timeout-at now)))})]
    (h-utils/json-response res)))

(defn session-api
  "Please note that these methods should be declared in the API"
  [request hard-timeout-at hard-timeout?]
  (case (:uri request)
    "/api/session/status"
    (session-status request hard-timeout-at hard-timeout?)

    "/api/session/timeout-re-auth"
    (-> (h-utils/json-response {:result "ok"})
        (assoc :session
               (merge (:session request)
                      {::re-auth-timeout-at (utils/current-time)})))

    "/api/session/timeout-hard"
    (-> (h-utils/json-response {:result "ok"})
        (assoc :session
               (merge (:session request)
                      {::hard-timeout-at (utils/current-time)})))

    "/api/session/timeout-hard-soon"
    (let [re-auth-timeout-at (get-in request [:session ::re-auth-timeout-at])]
      (-> (h-utils/json-response {:result "ok"})
          (assoc :session
                 (merge (:session request)
                        {::hard-timeout-at (+ (utils/current-time)
                                              (timeout-hard-soon-limit))}
                        (when re-auth-timeout-at
                          {::re-auth-timeout-at 0
                           :auth-re-auth?       true})))))

    "/api/session/renew"
    (let [session (:session request)]
      (cond
        (not (or (:external-login? session) (not (:user-id session))))
        (http-response/bad-request "Renew can only be used for non-user or external-login sessions")

        :else
        (-> (h-utils/json-response {:result "ok"})
            (assoc :session
                   (merge (:session request)
                          {::hard-timeout-at (+ (utils/current-time)
                                                (timeout-hard-limit))})))))))

;; -------------------
;;    HARD TIMEOUT
;; -------------------

(def ^:dynamic *timeout-hard-override* nil)

(defn- no-hard-timeout-response
  [handler request session-in now hard-timeout]
  (let [response        (handler request)
        session-out     (:session response)
        hard-timeout-at (if (and (:auth-re-auth? session-in) ; Do not reset hard timeout if re-auth needed
                                 (not (and (contains? session-out :auth-re-auth?)
                                           (false? (boolean (:auth-re-auth? session-out))))))
                          (::hard-timeout-at session-in)
                          (+ now hard-timeout))]
    (session-utils/assoc-out-session response session-in {::hard-timeout-at hard-timeout-at})))

(defn- wrap-session-hard-timeout*
  [handler request]
  (let [hard-timeout    (or *timeout-hard-override* (timeout-hard-limit))
        session-in      (:session request)
        now             (utils/current-time)
        hard-timeout-at (::hard-timeout-at session-in)
        hard-timeout?   (and hard-timeout-at (>= now hard-timeout-at))]
    (if (str/starts-with? (:uri request) "/api/session/")
      (session-api request hard-timeout-at hard-timeout?)
      (if hard-timeout?
        (let [response (handler (assoc request :session nil))]
          (binding [*in-session?* false]
            (assoc response :session nil)))
        (binding [*in-session?* (not (empty? session-in))]
          (no-hard-timeout-response handler request session-in now hard-timeout))))))

(defn wrap-session-hard-timeout
  [handler]
  (fn [request]
    (wrap-session-hard-timeout* handler request)))