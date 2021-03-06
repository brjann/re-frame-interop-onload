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
            [bass4.responses.auth :as auth-response]
            [bass4.assessment.ongoing :as assessment-ongoing]
            [bass4.services.user :as user-service]
            [bass4.services.bass :as bass]
            [clojure.tools.logging :as log]
            [bass4.api-coercion :as api :refer [defapi]]
            [bass4.config :as config]
            [bass4.services.privacy :as privacy-service]
            [bass4.clients.core :as clients]
            [bass4.php-interop :as php-interop]))


(defn ^:dynamic db-ext-login-settings
  [db]
  (db/ext-login-settings db {}))

;; ------------
;;  MIDDLEWARE
;; ------------

(defn logged-response
  [s]
  (when-not config/test-mode?
    (log/info (:db-name clients/*client-config*) s))
  (layout/text-response s))

(defn- match-request-ip
  [request ips-str]
  (when-not config/test-mode?
    (log/info "Request from " (h-utils/get-client-ip request)))
  (let [remote-ip   (h-utils/get-client-ip request)
        allowed-ips (into #{} (mapv #(first (string/split % #" ")) (string/split-lines ips-str)))]
    (contains? allowed-ips remote-ip)))

(defn check-ip-mw
  [handler request]
  (let [{:keys [allowed? ips]} (db-ext-login-settings db/*db*)]
    (cond
      (not allowed?)
      (logged-response "0 External login not allowed")

      (and
        (string/starts-with? (:uri request) "/ext-login/check-pending/")
        (not (match-request-ip request ips)))
      (logged-response (str "0 External login not allowed from this IP " (h-utils/get-client-ip request)))

      :else (handler request))))

(defn return-url-after-assessments
  [session]
  (and (not (:assessments-pending? session))
       (:assessments-checked? session)
       (:return-url session)))

(defn return-url-mw
  [handler request]
  (let [res (handler request)]
    (if-let [return-url (return-url-after-assessments (:session res))]
      (do
        (when-not config/test-mode?
          (log/info "Assessment completed for" (get-in res [:session :user-id]) "returning to" return-url))
        (-> (http-response/found return-url)
            (assoc :session {})))
      res)))


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
      user)))

(defn- uid-url
  [user-id request]
  (let [host     (h-utils/get-host request)
        scheme   (if (config/env :ssl)
                   "https"
                   (name (:scheme request)))
        filename (php-interop/write-session-file user-id "extlogin")]
    (str scheme "://" host "/ext-login/do-login?uid=" (url-encode filename))))

(defapi check-pending
  [participant-id :- [[api/str? 1 100]] request]
  (when-not config/test-mode?
    (log/info "Check pending for" participant-id))
  (let [user (check-participant-id participant-id)]
    ;; If error, then user is a string with error message
    (if (string? user)
      (logged-response (str "0 " user))
      (cond
        (and
          (not (privacy-service/privacy-notice-disabled?))
          (not (privacy-service/privacy-notice-exists? (:project-id user))))
        (logged-response "0 Privacy notice missing in DB")

        (zero? (count (assessment-ongoing/ongoing-assessments (:user-id user))))
        (logged-response "0 No pending administrations")

        :else
        (do
          (when-not config/test-mode?
            (log/info "Assessments pending for" (:user-id user)))
          (logged-response (uid-url (:user-id user) request)))))))

(defapi do-login
  [uid :- [[api/str? 1 100]]
   return-url :- [[api/str? 1 2000] api/url?]
   logout-url :- [:? [api/str? 1 2000] api/url?]]
  (if-let [user (-> (php-interop/read-session-file uid true 120)
                    (user-service/get-user))]
    (-> (http-response/found "/user")
        (assoc :session (auth-response/create-new-session user {:external-login? true
                                                                :return-url      return-url
                                                                :logout-path     logout-url})))
    (if return-url
      (http-response/found return-url)
      (http-response/bad-request "Bad UID and no return url"))))

;; ------------
;;    ROUTES
;; ------------


(defroutes ext-login-routes
  (context "/ext-login" [:as request]
    (GET "/check-pending/:participant-id" [participant-id]
      (check-pending participant-id request))
    (GET "/do-login" [uid returnURL logoutURL]
      (do-login uid returnURL logoutURL))))