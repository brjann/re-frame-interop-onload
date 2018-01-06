(ns bass4.middleware.embedded
  (:require [bass4.utils :refer [filter-map time+ nil-zero?]]
            [clojure.string :as string]
            [bass4.services.bass :as bass]
            [ring.util.http-response :as response]
            [bass4.layout :as layout]
            [clojure.tools.logging :as log]))


(defn embedded-session
  [handler request uid]
  (if-let [{:keys [user-id path]}
           (bass/embedded-session-file uid)]
    (-> (response/found path)
        (assoc :session {:identity user-id :embedded-path path}))
    (layout/text-response "Wrong uid.")))

(defn embedded-path
  [handler request]
  (let [current-path  (:uri request)
        embedded-path (get-in request [:session :embedded-path])]
    (if (and current-path embedded-path (string/starts-with? current-path embedded-path))
      (handler request)
      (layout/error-page
        {:status 403
         :title  "No embedded access"}))))

(defn handle-embedded
  [handler request]
  (let [path (:uri request)
        uid  (get-in request [:params :uid])]
    (if (string/starts-with? path "/embedded/create-session")
      (embedded-session handler request uid)
      (embedded-path handler request))))

(defn embedded
  [handler request]
  (if (when-let [path (:uri request)]
        (string/starts-with? path "/embedded"))
    (handle-embedded handler request)
    (handler request)))
