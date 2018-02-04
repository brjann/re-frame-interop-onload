(ns bass4.routes.ext-login
  (:require [bass4.layout :as layout]
            [bass4.services.auth :as auth]
            [compojure.core :refer [defroutes routes context GET POST ANY]]
            [bass4.db.core :as db]
            [ring.util.http-response :as http-response]
            [ring.util.response :as response]
            [ring.util.request :as request]
            [ring.util.codec :refer [url-encode]]
            [bass4.utils :refer [map-map str->int]]
            [bass4.config :refer [env]]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [bass4.bass-locals :as locals]
            [bass4.responses.instrument :as instruments]
            [bass4.responses.auth :as auth-response]
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

(defn- check-participant-id
  [participant-id]
  (let [users (user-service/get-users-by-participant-id participant-id)
        user  (first users)]
    (cond
      (nil? user)
      "No such user"

      (< 1 (count users))
      "More than 1 matching user"

      :else
      (:user-id user))))

(defn uid-url
  [user-id request]
  (let [headers  (:headers request)
        host     (get headers "x-forwarded-host" (get headers "host"))
        scheme   (name (:scheme request))
        filename (bass/write-session-file user-id "extlogin")]
    (str scheme "://" host "/ext-login/do-login?uid=" (url-encode filename))))

(defn- check-pending
  [participant-id request]
  (let [user-id (check-participant-id participant-id)]
    (if (string? user-id)
      (layout/text-response (str "0 " user-id))
      (cond
        (zero? (count (assessments/get-pending-assessments user-id)))
        (layout/text-response "0 No pending administrations")

        :else
        (layout/text-response (uid-url user-id request))))))

(defn- do-login
  [uid return-url]
  (when-let [user (-> (bass/read-session-file uid true)
                      (user-service/get-user))]
    (-> (http-response/found "/user/")
        (assoc :session (auth-response/create-new-session user {:external-login true :return-url return-url} true)))))

(defroutes ext-login-routes
  (context "/ext-login" [:as request]
    (GET "/check-pending/:participant-id" [participant-id] (check-pending participant-id request))
    (GET "/do-login" [& params]
      (let [uid        (:uid params)
            return-url (:returnURL params)]
        (do-login uid return-url)))))