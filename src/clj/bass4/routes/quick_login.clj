(ns bass4.routes.quick-login
  (:require [ring.util.http-response :as http-response]
            [compojure.core :refer [defroutes routes context GET POST ANY]]
            [clojure.core.async :refer [put!]]
            [ring.util.codec :refer [url-encode]]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [bass4.layout :as layout]
            [bass4.db.core :as db]
            [bass4.utils :refer [map-map str->int]]
            [bass4.config :refer [env]]
            [bass4.responses.auth :as auth-response]
            [bass4.services.bass :as bass]
            [bass4.clients.time :as client-time]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.config :as config]
            [bass4.passwords :as passwords]
            [bass4.utils :as utils])
  (:import (clojure.lang ExceptionInfo)))


;; ------------
;;   SERVICES
;; ------------

(defn db-quick-login-settings
  [db]
  (db/get-quick-login-settings db {}))

(def ^:dynamic *quick-login-updates-chan* nil)

(defn db-update-users-quick-login!
  [db users]
  (when (seq users)
    (let [updates (map #(vector (:user-id %) (:quick-login-id %) (:quick-login-timestamp %))
                       users)]
      (when *quick-login-updates-chan*
        (put! *quick-login-updates-chan* (into #{} (map :user-id users))))
      (db/update-users-quick-login! db {:quick-logins updates}))))

(def quicklogin-chars
  (vec "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"))

(defn quicklogin-id
  [user-id]
  (let [base64-id (->> (Integer/toBinaryString user-id)
                       (partition 6 6 [])
                       (map (comp #(Integer/parseInt % 2) #(apply str %)))
                       (map #(get quicklogin-chars %))
                       (apply str))
        rest      (passwords/letters-digits (- 11 (count base64-id)) quicklogin-chars)]
    (subs (str base64-id "." rest) 0 11)))

(defn recent-quick-login?
  [now user expiration-days]
  (let [timestamp (when (:quick-login-timestamp user)
                    (utils/from-unix (:quick-login-timestamp user)))]
    (cond
      (or (empty? (:quick-login-id user))
          (not timestamp))
      false

      (t/after? timestamp now)
      false

      (let [day-diff (t/in-days (t/interval timestamp now))]
        (>= expiration-days (+ day-diff 7)))
      true

      :else
      false)))

(defn update-users-quick-login!
  [db now users]
  (when (seq users)
    (let [expiration-days (:expiration-days (db-quick-login-settings db))
          update-users    (->> users
                               (remove #(recent-quick-login? now % expiration-days))
                               (mapv #(merge % {:quick-login-id        (quicklogin-id (:user-id %))
                                                :quick-login-timestamp (utils/to-unix now)})))]
      (db-update-users-quick-login! db update-users)
      update-users)))

(defmacro log-msg
  [& msgs]
  `(when-not config/test-mode?
     (log/info ~@msgs)))

(defn- quick-login-check-length
  [quick-login-id]
  (when (< 15 (count quick-login-id))
    (log-msg "Quick login was too long" (count quick-login-id))
    (throw (ex-info "Quick login too long" {:type ::quick-login-error}))))

(defn- quick-login-settings
  []
  (let [{:keys [expiration-days allowed?]} (db-quick-login-settings db/*db*)]
    (when (not allowed?)
      (log-msg "Quick login not allowed")
      (throw (ex-info "Quick login is not allowed" {:type ::quick-login-error})))
    expiration-days))

(defn- quick-login-user
  [quick-login-id]
  (let [users (db/get-user-by-quick-login-id {:quick-login-id quick-login-id})]
    (if-not (= 1 (count users))
      (log-msg "Incorrect number of matches" (count users))
      (first users))))

(defn quick-login-expired?
  [user expiration-days]
  (let [days-since (client-time/days-since-tz (:quick-login-timestamp user))]
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

(defapi quick-login
  [quick-login-id :- [[api/str? 1 20]]]
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
    (catch ExceptionInfo e
      (if (= ::quick-login-error (:type (.data e)))
        (layout/text-response (.getMessage e))
        (throw e)))))

(defroutes quick-login-routes
  (GET "/q/:quick-login-id" [quick-login-id]
    (quick-login quick-login-id)))