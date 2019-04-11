(ns bass4.session.timeout
  "Adapted from https://github.com/ring-clojure/ring-session-timeout"
  (:require [clojure.string :as str]
            [bass4.http-errors :as http-errors]
            [bass4.config :refer [env]]
            [bass4.session.create :as session-create]
            [bass4.utils :as utils]
            [ring.util.http-response :as http-response]
            [bass4.layout :as layout]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]))

;; -------------------
;;   RE-AUTH TIMEOUT
;; -------------------

(defn reset-re-auth
  [session]
  (dissoc session :auth-re-auth? ::re-auth-timeout-at))

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

    (> now soft-timeout-at)
    true

    :else false))

(defn- re-auth-response
  [request session]
  (-> (http-errors/re-auth-440 (str "/re-auth?return-url=" (request-string request)))
      (assoc :session (assoc session :auth-re-auth? true))))

(defn- no-re-auth-response
  [handler request session-in now re-auth-timeout]
  (let [response        (handler (assoc request :session (dissoc session-in :auth-re-auth?)))
        soft-timeout-at (+ now (dec re-auth-timeout))]      ; dec because comparison is strictly after
    (session-create/assoc-out-session response session-in {::re-auth-timeout-at soft-timeout-at})))

(defn- wrap-session-re-auth-timeout*
  [handler request re-auth-timeout]
  (if (or (str/starts-with? (:uri request) "/user/ui")
          (:external-login? (:session request)))
    (handler request)
    (let [session-in      (:session request)
          now             (utils/current-time)
          soft-timeout-at (::re-auth-timeout-at session-in)
          re-auth?        (should-re-auth? session-in now soft-timeout-at)]
      (if re-auth?
        (re-auth-response request session-in)
        (no-re-auth-response handler request session-in now re-auth-timeout)))))

(defn wrap-session-re-auth-timeout
  ([handler]
   (wrap-session-re-auth-timeout handler (* 60 60)))
  ([handler re-auth-timeout]
   (fn [request]
     (wrap-session-re-auth-timeout* handler request re-auth-timeout))))

;; -------------------
;;    HARD TIMEOUT
;; -------------------

(def ^:dynamic *timeout-hard-override* nil)

(defn- no-hard-timeout-response
  [handler request session-in now hard-timeout]
  (let [response        (handler request)
        hard-timeout-at (+ now (dec hard-timeout))]         ; dec because comparison is strictly after
    (session-create/assoc-out-session response session-in {::hard-timeout-at hard-timeout-at})))

(defn- session-status
  [request hard-timeout-at hard-timeout?]
  (let [session            (:session request)
        now                (utils/current-time)
        re-auth-timeout-at (::re-auth-timeout-at session)
        res                (when-not (or (empty? (dissoc session ::hard-timeout-at))
                                         hard-timeout?)
                             {:hard    (when hard-timeout-at
                                         (- hard-timeout-at now))
                              :re-auth (when re-auth-timeout-at
                                         (max 0 (- re-auth-timeout-at now)))})]
    (-> res
        (json/write-str)
        (http-response/ok)
        (assoc :headers {"Content-type" "application/json"}))))

(defn- wrap-session-hard-timeout*
  [handler request hard-timeout]
  (let [hard-timeout    (or *timeout-hard-override* hard-timeout)
        session-in      (:session request)
        now             (utils/current-time)
        hard-timeout-at (::hard-timeout-at session-in)
        hard-timeout?   (and hard-timeout-at (> now hard-timeout-at))]
    (if (= "/api/session-status" (:uri request))
      (session-status request hard-timeout-at hard-timeout?)
      (if hard-timeout?
        (let [response (handler (assoc request :session nil))]
          (assoc response :session nil))
        (no-hard-timeout-response handler request session-in now hard-timeout)))))

(defn wrap-session-hard-timeout
  ([handler]
   (wrap-session-hard-timeout handler (* 2 60 60)))
  ([handler hard-timeout]
   (fn [request]
     (wrap-session-hard-timeout* handler request hard-timeout))))