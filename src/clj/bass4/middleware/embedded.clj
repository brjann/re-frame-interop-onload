(ns bass4.middleware.embedded
  (:require [bass4.utils :refer [filter-map time+ nil-zero?]]
            [clojure.string :as string]
            [bass4.services.bass :as bass]
            [ring.util.http-response :as http-response]
            [bass4.layout :as layout]
            [clojure.tools.logging :as log]
            [bass4.services.bass :as bass-service]
            [clj-time.core :as t]
            [bass4.time :as b-time]
            [bass4.utils :as utils]
            [clojure.string :as str]))

(defn get-session-file
  [uid]
  (let [info (bass/read-session-file uid)]
    (assoc info :user-id (utils/str->int (:user-id info)))))

(defn create-session
  [handler request uid]
  (if-let [{:keys [user-id path php-session-id]} (get-session-file uid)]
    (let [session             (:session request)
          prev-embedded-paths (if (and (:embedded-paths session)
                                       (= (:user-id session) user-id)
                                       (= (:php-session-id session) php-session-id))
                                (:embedded-paths session)
                                #{})
          embedded-paths      (conj prev-embedded-paths path)]
      (-> (http-response/found path)
          (assoc :session {:user-id        user-id
                           :embedded-paths embedded-paths
                           :php-session-id php-session-id})))
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
  (if-let [php-session (bass-service/get-php-session php-session-id)]
    (let [last-activity      (:last-activity php-session)
          php-user-id        (:user-id php-session)
          now-unix           (b-time/to-unix (t/now))
          time-diff-activity (- now-unix last-activity)
          timeouts           (bass-service/get-staff-timeouts)
          re-auth-timeout    (:re-auth-timeout timeouts)
          absolute-timeout   (:absolute-timeout timeouts)]
      (cond
        (not= user-id php-user-id)
        ::user-mismatch

        (>= time-diff-activity absolute-timeout)
        ::absolute-timeout

        (>= time-diff-activity re-auth-timeout)
        ::re-auth-timeout

        :else
        ::ok))
    ::no-session))

(defn check-embedded-path
  [handler request]
  (let [current-path   (:uri request)
        session        (:session request)
        embedded-paths (:embedded-paths session)
        php-session-id (:php-session-id session)]
    (if (string/starts-with? current-path "/embedded/error/")
      (handler request)
      (if (and embedded-paths (some #(matches-embedded current-path (str "/embedded/" %)) embedded-paths))
        (case (check-php-session (:session request))
          (::user-mismatch ::no-session ::absolute-timeout)
          (->
            (http-response/found "/embedded/error/no-session")
            (assoc :session {}))

          ::re-auth-timeout
          (http-response/found "/embedded/error/re-auth")

          ::ok
          (do
            (bass-service/update-php-session-last-activity! php-session-id (b-time/to-unix (t/now)))
            (handler request)))
        (http-response/forbidden "No embedded access")))))

(defn embedded-request [handler request uid]
  ;; Note that if url uid is attached to already legal embedded path
  ;; - user is thrown out because only the uid file's embedded info
  ;; is included.
  (let [url-uid-session (when uid
                          (let [{:keys [user-id path php-session-id]}
                                (get-session-file uid)]
                            {:user-id user-id :embedded-paths #{path} :php-session-id php-session-id}))]
    (check-embedded-path handler (update request :session #(merge % url-uid-session)))))

(defn handle-embedded
  [handler request]
  (let [path (:uri request)
        uid  (get-in request [:params :uid])]
    (if (string/starts-with? path "/embedded/create-session")
      (create-session handler request uid)
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
      (if (string/starts-with? path "/embedded/iframe")
        (update-in response [:headers] #(-> %
                                            (dissoc "X-Frame-Options")
                                            (assoc "X-XSS-Protection" "0")))
        response)
      response)))