(ns bass4.session.timeout
  "Adapted from https://github.com/ring-clojure/ring-session-timeout"
  (:require [bass4.http-errors :as http-errors]
            [clojure.tools.logging :as log]
            [bass4.config :refer [env]]
            [clj-time.core :as t]
            [clojure.string :as str]
            [bass4.session.create :as session-create]))



(defn current-time
  []
  (t/now))

#_(defn- current-time []
    (quot (System/currentTimeMillis) 1000))


;; -------------------
;;   RE-AUTH TIMEOUT
;; -------------------

(defn reset-re-auth
  [session]
  (dissoc session :auth-re-auth? :re-auth-timeout-at))

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

(defn no-re-auth-response
  [handler request session-in now re-auth-timeout]
  (let [response         (handler (assoc request :session (dissoc session-in :auth-re-auth?)))
        soft-timeout-at  (t/plus now (t/seconds (dec re-auth-timeout))) ; dec because comparison is strictly after
        session-out      (:session response)
        session-deleted? (and (contains? response :session) (nil? session-out))]
    (session-create/assoc-out-session response session-in {:re-auth-timeout-at soft-timeout-at})
    #_(if session-deleted?
        response
        (assoc response :session (merge (if (nil? session-out)
                                          session-in
                                          session-out)
                                        {:re-auth-timeout-at soft-timeout-at})))))

(defn wrap-session-re-auth-timeout
  ([handler]
   (wrap-session-re-auth-timeout handler (* 60 60)))
  ([handler re-auth-timeout]
   (fn [request]
     (if (or (str/starts-with? (:uri request) "/user/ui")
             (:external-login? (:session request)))
       (handler request)
       (let [session-in      (:session request)
             now             (current-time)
             soft-timeout-at (:re-auth-timeout-at session-in)
             re-auth?        (should-re-auth? session-in now soft-timeout-at)]
         (if re-auth?
           (re-auth-response request session-in)
           (no-re-auth-response handler request session-in now re-auth-timeout)))))))

;; -------------------
;;    HARD TIMEOUT
;; -------------------

(def ^:dynamic *timeout-hard-override* nil)

(defn no-hard-timeout-response
  [handler request session-in now hard-timeout]
  (let [response        (handler request)
        hard-timeout-at (t/plus now (t/seconds (dec hard-timeout))) ; dec because comparison is strictly after
        session-out     (:session response)
        new-session     (merge (if (nil? session-out)
                                 session-in
                                 session-out)
                               {:hard-timeout-at hard-timeout-at})]
    #_(assoc response :session new-session)
    (session-create/assoc-out-session response session-in {:hard-timeout-at hard-timeout-at})))

(defn wrap-session-hard-timeout
  ([handler]
   (wrap-session-hard-timeout handler (* 2 60 60)))
  ([handler hard-timeout]
   (fn [request]
     (let [hard-timeout    (or *timeout-hard-override* hard-timeout)
           session-in      (:session request)
           now             (current-time)
           hard-timeout-at (:hard-timeout-at session-in)
           hard-timeout?   (t/after? now hard-timeout-at)]
       (if hard-timeout?
         (let [response (handler (assoc request :session {}))]
           (assoc response :session {}))
         (no-hard-timeout-response handler request session-in now hard-timeout))))))