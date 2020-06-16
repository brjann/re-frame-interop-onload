(ns bass4.session.api
  (:require [bass4.utils :as utils]
            [ring.util.http-response :as http-response]
            [bass4.http-utils :as h-utils]
            [clojure.data.json :as json]
            [bass4.middleware.request-logger :as request-logger]
            [bass4.session.utils :as session-utils]
            [bass4.clients.core :as clients]))

;; ------------------------
;;    SESSION STATUS API
;; ------------------------

(defn timeout-hard-soon-limit
  []
  (clients/client-setting [:timeout-hard-soon]))

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
                                                  (not (session-utils/no-re-auth? session)))
                                         (max 0 (- re-auth-timeout-at now)))})]
    res))

(defn session-api
  "Please note that these methods should be declared in the API"
  [request hard-timeout-at hard-timeout? timeout-hard-limit timeout-hard-soon-limit]
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