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
            [ring.util.http-response :as http-response]))


(defn- quick-login-check-length
  [quick-login-id]
  (when (< 15 (count quick-login-id))
    (log/info "Quick login was too long" (count quick-login-id))
    (throw (Exception. "Quick login too long"))))

(defn- quick-login-settings
  []
  (let [{:keys [expiration-days allowed?]} (db/bool-cols db/get-quick-login-settings [:allowed?])]
    (when (not allowed?)
      (log/info "Quick login not allowed")
      (throw (Exception. "Quick login is not allowed")))
    expiration-days))

(defn- quick-login-user
  [quick-login-id]
  (let [users (db/get-user-by-quick-login-id {:quick-login-id quick-login-id})]
    (if-not (= 1 (count users))
      (log/info "Incorrect number of matches" (count users))
      (first users))))

(defn quick-login-expired?
  [user expiration-days]
  (let [days-since (b-time/days-since-tz (:quick-login-timestamp user))]
    (when (<= expiration-days days-since)
      (log/info "Quick login expired")
      true)))

(defn expired-response
  [user]
  (let [emails (bass/db-contact-info (:project-id user))]
    (layout/render "quick-login-expired.html"
                   {:email (or (:project-email emails)
                               (:db-email emails))})))

(defn invalid-response
  []
  (let [emails (bass/db-contact-info (:project-id 0))]
    (layout/render "quick-login-invalid.html"
                   {:email (:db-email emails)})))

(defn do-login
  [user]
  (-> (http-response/found "/user/")
      (assoc :session (auth-response/create-new-session user {:external-login true} true))))

(defn- quick-login
  [quick-login-id]
  (try
    (quick-login-check-length quick-login-id)
    (log/info "Checking quick-login ID" quick-login-id)
    (let [expiration-days (quick-login-settings)
          user            (quick-login-user quick-login-id)]
      (if (nil? user)
        (invalid-response)
        (if (quick-login-expired? user expiration-days)
          (expired-response user)
          (do-login user))))
    (catch Exception e
      (layout/text-response (:cause (Throwable->map e))))))

(defroutes quick-login-routes
  (GET "/q/:quick-login-id" [quick-login-id]
    (quick-login quick-login-id)))