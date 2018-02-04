(ns bass4.routes.ext-login
  (:require [bass4.layout :as layout]
            [bass4.services.auth :as auth]
            [compojure.core :refer [defroutes routes context GET POST ANY]]
            [bass4.db.core :as db]
            [ring.util.http-response :as http-response]
            [ring.util.response :as response]
            [ring.util.request :as request]
            [bass4.utils :refer [map-map str->int]]
            [bass4.config :refer [env]]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [bass4.bass-locals :as locals]
            [bass4.responses.instrument :as instruments]
            [bass4.services.assessments :as assessments]
            [bass4.services.user :as user-service]
            [bass4.services.bass :as bass]
            [mount.core :as mount]
            [clojure.pprint]
            [bass4.request-state :as request-state]
            [ring.util.codec :as codec]
            [clojure.tools.logging :as log]))

(defn- match-request-ip
  [request ips-str]
  (let [remote-ip   (:remote-addr request)
        allowed-ips (into #{} (mapv #(first (string/split % #" ")) (string/split-lines ips-str)))]
    (contains? allowed-ips remote-ip)))

(defn check-ip-mw
  [handler request]
  (let [{:keys [allowed ips]} (db/bool-cols db/ext-login-settings {} [:allowed])]
    (cond
      (not allowed) (layout/text-response "0 External login not allowed")
      (not (match-request-ip request ips)) (layout/text-response (str "0 External login not allowed from this IP " (:remote-addr request)))
      :else (handler request))))

(defn check-pending
  [participant-id]
  (let [users (user-service/get-users-by-participant-id participant-id)
        user  (first users)]
    (cond (nil? user)
          (layout/text-response "0 No such user")

          (< 1 (count users))
          (layout/text-response "0 More than 1 matching user")

          :else
          (assessments/get-pending-assessments (:user-id user)))))

(defroutes ext-login-routes
  (context "/ext-login" [:as request]
    (GET "/check-pending/:participant-id" [participant-id] (check-pending participant-id))))