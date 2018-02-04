(ns bass4.middleware.embedded
  (:require [bass4.utils :refer [filter-map time+ nil-zero?]]
            [clojure.string :as string]
            [bass4.services.bass :as bass]
            [ring.util.http-response :as response]
            [bass4.layout :as layout]
            [ring.util.response :as response-utils]
            [clojure.tools.logging :as log]))

(defn embedded-session
  [handler request uid]
  (if-let [{:keys [user-id path]}
           (bass/embedded-session-file uid)]
    (-> (response/found path)
        (assoc :session {:identity user-id :embedded-path path}))
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

(defn check-embedded-path
  [handler request]
  (let [current-path  (:uri request)
        embedded-path (get-in request [:session :embedded-path])]
    (if (and embedded-path (matches-embedded current-path (str "/embedded/" embedded-path)))
      (handler request)
      (layout/error-page
        {:status 403
         :title  "No embedded access"}))))

(defn embedded-request [handler request uid]
  (let [session-map (when uid
                      (let [{:keys [user-id path]}
                            (bass/embedded-session-file uid)]
                        {:identity user-id :embedded-path path}))]
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