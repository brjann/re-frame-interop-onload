(ns bass4.middleware.embedded
  (:require [bass4.utils :refer [filter-map time+ nil-zero?]]
            [clojure.string :as string]
            [bass4.services.bass :as bass]
            [ring.util.http-response :as http-response]
            [bass4.layout :as layout]
            [clojure.tools.logging :as log]
            [bass4.services.bass :as bass-service]
            [clj-time.core :as t]
            [bass4.time :as b-time]))

(defn embedded-session
  [handler request uid]
  (if-let [{:keys [user-id path session-id]} (bass/read-session-file uid)]
    (-> (http-response/found path)
        (assoc :session {:user-id        user-id
                         :embedded-path  path
                         :php-session-id session-id}))
    (layout/print-var-response "Wrong uid.")))

(defn legal-character
  [c]
  (some #(= c %) ["=" "/" "?" "&"]))

(defn matches-embedded
  [current embedded]
  (let [current-length  (count current)
        embedded-length (count embedded)]
    (and
      current
      embedded
      (string/starts-with? current embedded)
      (if (> current-length embedded-length)
        (or (legal-character (subs current (dec embedded-length) embedded-length))
            (legal-character (subs current embedded-length (inc embedded-length))))
        true))))

(defn check-php-session
  [{:keys [user-id php-session-id]}]
  (let [php-session        (bass-service/get-php-session php-session-id)
        ;; TODO: If session doesn't exist
        last-activity      (:last-activity php-session)
        php-user-id        (:user-id php-session)
        now-unix           (b-time/to-unix (t/now))
        time-diff-activity (- now-unix last-activity)
        timeouts           (bass-service/get-staff-timeouts)
        re-auth-timeout    (:re-auth-timeout timeouts)
        absolute-timeout   (:absolute-timeout timeouts)]
    (log/debug "user-id:" user-id "php user-id:" php-user-id)
    (log/debug "current time:" now-unix "last session activity:" last-activity "diff" time-diff-activity)
    (log/debug "re-auth timeout:" re-auth-timeout "secs left" (- re-auth-timeout time-diff-activity))
    (log/debug "absolute timeout:" absolute-timeout "secs left" (- absolute-timeout time-diff-activity))))

(defn check-embedded-path
  [handler request]
  (let [current-path  (:uri request)
        embedded-path (get-in request [:session :embedded-path])]
    (check-php-session (:session request))
    (if (and embedded-path (matches-embedded current-path (str "/embedded/" embedded-path)))
      (handler request)
      (layout/error-page
        {:status 403
         :title  "No embedded access"}))))

(defn embedded-request [handler request uid]
  (let [session-map (when uid
                      (let [{:keys [user-id path php-session-id]}
                            (bass/read-session-file uid)]
                        {:user-id user-id :embedded-path path :php-session-id php-session-id}))]
    (check-embedded-path handler (update request :session #(merge % session-map)))))

(defn handle-embedded
  [handler request]
  (let [path (:uri request)
        uid  (get-in request [:params :uid])]
    (if (string/starts-with? path "/embedded/create-session")
      (embedded-session handler request uid)
      (embedded-request handler request uid))))

(defn embedded-mw
  [handler request]
  (if (when-let [path (:uri request)]
        (string/starts-with? path "/embedded"))
    (handle-embedded handler request)
    (handler request)))


(defn embedded-iframe
  [handler request]
  (let [response (handler request)]
    (if-let [path (:uri request)]
      (if (string/starts-with? path "/embedded/")
        (update-in response [:headers] #(dissoc % "X-Frame-Options"))
        response)
      response)))