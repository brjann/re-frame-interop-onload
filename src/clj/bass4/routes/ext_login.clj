(ns bass4.routes.ext-login
  (:require [bass4.layout :as layout]
            [bass4.http-utils :as h-utils]
            [compojure.core :refer [defroutes routes context GET POST ANY]]
            [bass4.db.core :as db]
            [ring.util.http-response :as http-response]
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
            [bass4.api-coercion :as api :refer [def-api]]
            [bass4.time :as b-time]))


;; ------------
;;  MIDDLEWARE
;; ------------

(defn logged-response
  [s]
  (log/info (:db-name db-config/*local-config*) s)
  (layout/text-response s))

(defn- match-request-ip
  [request ips-str]
  (log/info "Request from " (h-utils/get-ip request))
  (let [remote-ip   (h-utils/get-ip request)
        allowed-ips (into #{} (mapv #(first (string/split % #" ")) (string/split-lines ips-str)))]
    (contains? allowed-ips remote-ip)))

(defn check-ip-mw
  [handler request]
  (let [{:keys [allowed ips]} (db/bool-cols db/ext-login-settings {} [:allowed])]
    (cond
      (not allowed)
      (logged-response "0 External login not allowed")

      (and
        (string/starts-with? (:uri request) "/ext-login/check-pending/")
        (not (match-request-ip request ips)))
      (logged-response (str "0 External login not allowed from this IP " (h-utils/get-ip request)))

      :else (handler request))))

(defn return-url-mw
  [handler request]
  (let [session (:session request)]
    (if (and (:return-url session) (not (:assessments-pending? session)))
      (-> (http-response/found (:return-url session))
          (assoc :session {}))
      (handler request))))


;; ------------
;;  RESPONSES
;; ------------


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

(defn- uid-url
  [user-id request]
  (let [host     (h-utils/get-host request)
        scheme   (name (:scheme request))
        filename (bass/write-session-file user-id "extlogin")]
    (str scheme "://" host "/ext-login/do-login?uid=" (url-encode filename))))

(def-api check-pending
  [participant-id :- api/Str+ request]
  (log/info "Check pending for" participant-id)
  (let [user-id (check-participant-id participant-id)]
    (if (string? user-id)
      (logged-response (str "0 " user-id))
      (cond
        (zero? (count (assessments/get-pending-assessments user-id)))
        (logged-response "0 No pending administrations")

        :else
        (logged-response (uid-url user-id request))))))

(def-api do-login
  [uid :- api/Str+ return-url :- api/URL]
  (if-let [user (-> (bass/read-session-file uid true 120)
                    (user-service/get-user))]
    (-> (http-response/found "/user/")
        (assoc :session (auth-response/create-new-session user {:external-login? true :return-url return-url} true)))
    (if return-url
      (http-response/found return-url)
      (layout/error-400-page "Bad UID and no return url"))))

;; ------------
;;    ROUTES
;; ------------


(defroutes ext-login-routes
  (context "/ext-login" [:as request]
    (GET "/check-pending/:participant-id" [participant-id]
      (check-pending participant-id request))
    (GET "/do-login" [uid returnURL]
      (do-login uid returnURL))))