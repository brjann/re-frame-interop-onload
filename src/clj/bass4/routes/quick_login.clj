(ns bass4.routes.quick-login
  (:require [bass4.layout :as layout]
            [compojure.core :refer [defroutes routes context GET POST ANY]]
            [bass4.db.core :as db]
            [ring.util.codec :refer [url-encode]]
            [bass4.utils :refer [map-map str->int]]
            [bass4.config :refer [env]]
            [clojure.string :as string]
            [bass4.db-config :as db-config]
            [bass4.responses.auth :as auth-response]
            [bass4.services.assessments :as assessments]
            [bass4.services.user :as user-service]
            [bass4.services.bass :as bass]
            [clojure.tools.logging :as log]
            [bass4.time :as b-time]
            [ring.util.http-response :as http-response]
            [bass4.api-coercion :as api :refer [def-api]]
            [bass4.config :as config]))

(defmacro log-msg
  [& msgs]
  `(when-not config/test-mode?
     (log/debug ~@msgs)))

(defn- quick-login-check-length
  [quick-login-id]
  (when (< 15 (count quick-login-id))
    (log-msg "Quick login was too long" (count quick-login-id))
    (throw (Exception. "Quick login too long"))))

(defn- quick-login-settings
  []
  (let [{:keys [expiration-days allowed?]} (db/bool-cols db/get-quick-login-settings [:allowed?])]
    (when (not allowed?)
      (log-msg "Quick login not allowed")
      (throw (Exception. "Quick login is not allowed")))
    expiration-days))

(defn- quick-login-user
  [quick-login-id]
  (let [users (db/get-user-by-quick-login-id {:quick-login-id quick-login-id})]
    (if-not (= 1 (count users))
      (log-msg "Incorrect number of matches" (count users))
      (first users))))

(defn quick-login-expired?
  [user expiration-days]
  (let [days-since (b-time/days-since-tz (:quick-login-timestamp user))]
    (when (<= expiration-days days-since)
      (log-msg "Quick login expired")
      true)))

(defn expired-response
  [user]
  (let [emails (bass/db-contact-info (:project-id user))]
    (layout/render "quick-login-expired.html"
                   {:email (:email emails)})))

(defn invalid-response
  []
  (let [emails (bass/db-contact-info (:project-id 0))]
    (layout/render "quick-login-invalid.html"
                   {:email (:email emails)})))

(defn do-login
  [user]
  (-> (http-response/found "/user/")
      (assoc :session (auth-response/create-new-session user {:external-login? true :limited-access? true}))))

(def-api quick-login
  [quick-login-id :- api/str+!]
  (try
    (quick-login-check-length quick-login-id)
    (log-msg "Checking quick-login ID" quick-login-id)
    (let [expiration-days (quick-login-settings)
          user            (quick-login-user quick-login-id)]
      (if (nil? user)
        (invalid-response)
        (if (quick-login-expired? user expiration-days)
          (expired-response user)
          (do-login user))))
    ;; TODO: Why is Exception forwarded to user?
    (catch Exception e
      (layout/text-response (:cause (Throwable->map e))))))

(defroutes quick-login-routes
  (GET "/q/:quick-login-id" [quick-login-id]
    (quick-login quick-login-id)))