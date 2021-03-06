(ns bass4.session.timeout
  "Adapted from https://github.com/ring-clojure/ring-session-timeout"
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [ring.util.http-response :as http-response]
            [bass4.http-errors :as http-errors]
            [bass4.config :refer [env]]
            [bass4.session.utils :as session-utils]
            [bass4.utils :as utils]
            [bass4.http-utils :as h-utils]
            [bass4.middleware.request-logger :as request-logger]
            [bass4.clients.core :as clients]))

(def ^:dynamic *in-session?* false)
(def ^:dynamic *user-id* false)

(defn timeout-hard-limit
  []
  (clients/client-setting [:timeout-hard]))

(defn timeout-hard-soon-limit
  []
  (clients/client-setting [:timeout-hard-soon]))

(defn timeout-re-auth-limit
  []
  (clients/client-setting [:timeout-soft]))

(defn timeout-hard-short-limit
  []
  (clients/client-setting [:timeout-hard-short]))

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

(defn should-re-auth?
  [session time]
  (let [re-auth-timeout-at (::re-auth-timeout-at session)]
    (cond
      (:auth-re-auth? session)
      true

      (nil? re-auth-timeout-at)
      false

      (>= time re-auth-timeout-at)
      true

      :else false)))

(defn no-re-auth?
  [session]
  (or (not (:user-id session))
      (:external-login? session)))

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
  ;; Pass through requests to pluggable ui, it won't be
  ;; able to access API if re-auth has happened
  (if (or (str/starts-with? (:uri request) "/user/ui")
          (no-re-auth? (:session request)))
    (handler request)
    (let [session-in (:session request)]
      (if (should-re-auth? session-in (utils/current-time))
        (re-auth-response request session-in)
        (no-re-auth-response handler request session-in)))))

;; Only included in "/user/*" "/user" "/api/user/*" paths
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
                                                  (not (no-re-auth? session)))
                                         (max 0 (- re-auth-timeout-at now)))})]
    res))

(defn session-api
  "Please note that these methods should be declared in the API"
  [request hard-timeout-at hard-timeout? timeout-hard-limit]
  (let [response (case (:uri request)
                   "/api/session/user-id"
                   (http-response/ok
                     (when-let [user-id (get-in request [:session :user-id])]
                       {:user-id user-id}))

                   "/api/session/status"
                   (http-response/ok
                     (session-status request hard-timeout-at hard-timeout?))

                   "/api/session/timeout-re-auth"
                   (-> (http-response/ok {:result "ok"})
                       (assoc :session
                              (merge (:session request)
                                     {::re-auth-timeout-at (utils/current-time)})))

                   "/api/session/timeout-hard"
                   (-> (http-response/ok {:result "ok"})
                       (assoc :session
                              (merge (:session request)
                                     {::hard-timeout-at (utils/current-time)})))

                   "/api/session/timeout-hard-soon"
                   (let [re-auth-timeout-at (get-in request [:session ::re-auth-timeout-at])]
                     (-> (http-response/ok {:result "ok"})
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
                       (-> (http-response/ok {:result "ok"})
                           (assoc :session
                                  (merge (:session request)
                                         {::hard-timeout-at (+ (utils/current-time)
                                                               timeout-hard-limit)})))))

                   ;default
                   (http-response/not-found))]
    (let [body     (:body response)
          response (cond
                     (and (or (map? body) (nil? body))
                          (h-utils/ajax? request))
                     (-> response
                         (assoc :body (json/write-str body))
                         (http-response/content-type "application/json"))

                     (map? (:body response))
                     (-> response
                         (assoc :body (json/write-str body))
                         (http-response/content-type "text/plain"))

                     :else
                     (-> response
                         (http-response/content-type "text/plain")))]
      (if-not (= 404 (:status response))
        (assoc response ::request-logger/no-log? true)
        response))))

;; -------------------
;;    HARD TIMEOUT
;; -------------------

(def ^:dynamic *timeout-hard-override* nil)

(defn hard-timeout-map
  [external-login?]
  {::hard-timeout-at (+ (utils/current-time)
                        (if external-login?
                          (timeout-hard-short-limit)
                          (timeout-hard-limit)))})

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
  (if (empty? (:session request))
    (if (str/starts-with? (:uri request) "/api/session/")
      (-> (http-response/ok)
          (assoc :body (json/write-str nil))
          (http-response/content-type "application/json"))
      (handler request))
    (let [hard-timeout    (or *timeout-hard-override*
                              (if (no-re-auth? (:session request))
                                (timeout-hard-short-limit)
                                (timeout-hard-limit)))
          session-in      (:session request)
          now             (utils/current-time)
          hard-timeout-at (::hard-timeout-at session-in)
          hard-timeout?   (and hard-timeout-at (>= now hard-timeout-at))]
      (if (str/starts-with? (:uri request) "/api/session/")
        (session-api request hard-timeout-at hard-timeout? hard-timeout)
        (if hard-timeout?
          (let [response (handler (assoc request :session nil))]
            (binding [*in-session?* false]
              (assoc response :session nil)))
          (binding [*in-session?* (not (empty? session-in))
                    *user-id*     (:user-id session-in)]
            (no-hard-timeout-response handler request session-in now hard-timeout)))))))

(defn wrap-session-hard-timeout
  [handler]
  (fn [request]
    (wrap-session-hard-timeout* handler request)))